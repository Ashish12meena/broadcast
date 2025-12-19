package com.aigreentick.services.messaging.broadcast.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchUpdateService {

    private final ReportServiceImpl reportService;

    @Value("${batch.size:60}")
    private int batchSize;

    @Value("${batch.timeout-ms:5000}")
    private long batchTimeoutMs;

    // Store pending updates per broadcastId
    private final ConcurrentHashMap<Long, BroadcastBatch> broadcastBatches = new ConcurrentHashMap<>();

    /**
     * Add update to batch and wait for batch to complete.
     * Thread waits here until batch is full or timeout occurs.
     */
    public void addUpdateAndWait(
            Long broadcastId,
            String mobile,
            String responseJson,
            MessageStatus messageStatus,
            String whatsappMessageId) throws InterruptedException {

        BroadcastBatch batch = broadcastBatches.computeIfAbsent(broadcastId, k -> {
            log.debug("Created new batch for broadcastId={}", broadcastId);
            return new BroadcastBatch(batchSize, batchTimeoutMs);
        });

        // Add update and get the latch to wait on
        CountDownLatch latch = batch.addUpdate(new UpdateRequest(
            mobile, responseJson, messageStatus, whatsappMessageId, LocalDateTime.now()
        ));

        // Check if this thread should process the batch
        if (batch.shouldProcess()) {
            processBatch(broadcastId, batch);
        }

        // Wait for batch processing to complete
        boolean completed = latch.await(batchTimeoutMs + 5000, TimeUnit.MILLISECONDS);
        
        if (!completed) {
            log.warn("Batch processing timeout for broadcastId={} mobile={}", broadcastId, mobile);
        }
    }

    /**
     * Process all updates in the batch
     */
    private void processBatch(Long broadcastId, BroadcastBatch batch) {
        List<UpdateRequest> updates = batch.getUpdatesForProcessing();
        
        if (updates.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("Processing batch for broadcastId={}. Updates: {}", broadcastId, updates.size());
        
        try {
            int successCount = 0;
            int failCount = 0;

            for (UpdateRequest req : updates) {
                try {
                    int updated = reportService.updateReportByBroadcastIdAndMobile(
                        broadcastId,
                        req.mobile,
                        req.responseJson,
                        req.messageStatus,
                        req.whatsappMessageId,
                        req.timestamp
                    );

                    if (updated > 0) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to update report. broadcastId={} mobile={}", 
                        broadcastId, req.mobile, e);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch processed for broadcastId={}. Success: {}, Failed: {}, Duration: {}ms",
                broadcastId, successCount, failCount, duration);

        } finally {
            // Release all waiting threads
            batch.completeBatch();
            
            // Cleanup if batch is fully processed
            if (batch.isCompleted()) {
                broadcastBatches.remove(broadcastId);
                log.debug("Removed completed batch for broadcastId={}", broadcastId);
            }
        }
    }

    /**
     * Holds batch data for a single broadcast
     */
    private static class BroadcastBatch {
        private final List<UpdateRequest> updates = new ArrayList<>();
        private final int maxSize;
        private final long creationTime;
        private final long timeoutMs;
        private CountDownLatch currentLatch;
        private boolean processing = false;
        private boolean completed = false;

        public BroadcastBatch(int maxSize, long timeoutMs) {
            this.maxSize = maxSize;
            this.timeoutMs = timeoutMs;
            this.creationTime = System.currentTimeMillis();
            this.currentLatch = new CountDownLatch(1);
        }

        public synchronized CountDownLatch addUpdate(UpdateRequest request) {
            if (completed) {
                // Create new batch if previous is completed
                currentLatch = new CountDownLatch(1);
                updates.clear();
                processing = false;
                completed = false;
            }

            updates.add(request);
            return currentLatch;
        }

        public synchronized boolean shouldProcess() {
            if (processing || completed) {
                return false;
            }

            // Process if batch is full OR timeout reached
            boolean isFull = updates.size() >= maxSize;
            boolean isTimeout = (System.currentTimeMillis() - creationTime) >= timeoutMs;

            if (isFull || (isTimeout && !updates.isEmpty())) {
                processing = true;
                return true;
            }

            return false;
        }

        public synchronized List<UpdateRequest> getUpdatesForProcessing() {
            if (!processing) {
                return new ArrayList<>();
            }
            return new ArrayList<>(updates);
        }

        public synchronized void completeBatch() {
            completed = true;
            currentLatch.countDown();
        }

        public synchronized boolean isCompleted() {
            return completed;
        }
    }

    /**
     * Request to update a report
     */
    private record UpdateRequest(
        String mobile,
        String responseJson,
        MessageStatus messageStatus,
        String whatsappMessageId,
        LocalDateTime timestamp
    ) {}
}