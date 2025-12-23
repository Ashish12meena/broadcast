package com.aigreentick.services.messaging.broadcast.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.client.dto.FacebookApiResponse;
import com.aigreentick.services.messaging.broadcast.client.service.impl.WhatsappClient;
import com.aigreentick.services.messaging.broadcast.dto.response.SendTemplateMessageResponse;
import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;
import com.aigreentick.services.messaging.broadcast.kafka.event.BroadcastReportEvent;
import com.aigreentick.services.messaging.config.ExecutorConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BatchCoordinator {

    private final WhatsappClient whatsappClient;
    private final ReportServiceImpl reportService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Semaphore> userSemaphores;
    private final ConcurrentHashMap<String, Long> semaphoreLastUsed;

    @Value("${batch.size:80}")
    private int batchSize;

    @Value("${batch.timeout-ms:3000}")
    private long batchTimeoutMs;

    // Per-user queues and processors
    private final ConcurrentHashMap<String, UserBatchProcessor> userProcessors = new ConcurrentHashMap<>();

    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    public BatchCoordinator(
            WhatsappClient whatsappClient,
            ReportServiceImpl reportService,
            ObjectMapper objectMapper,
            ConcurrentHashMap<String, Semaphore> userSemaphores,
            ConcurrentHashMap<String, Long> semaphoreLastUsed) {
        this.whatsappClient = whatsappClient;
        this.reportService = reportService;
        this.objectMapper = objectMapper;
        this.userSemaphores = userSemaphores;
        this.semaphoreLastUsed = semaphoreLastUsed;
    }

    /**
     * Add event to user's queue. NEVER blocks, NEVER drops messages.
     */
    public void addEventToBatch(BroadcastReportEvent event, Acknowledgment acknowledgment) {
        if (shutdownRequested.get()) {
            log.warn("Shutdown requested, acknowledging message without processing");
            acknowledgment.acknowledge();
            return;
        }

        String phoneNumberId = event.getPhoneNumberId();

        // Get or create processor for this user
        UserBatchProcessor processor = userProcessors.computeIfAbsent(
                phoneNumberId,
                k -> {
                    UserBatchProcessor newProcessor = new UserBatchProcessor(
                            phoneNumberId,
                            batchSize,
                            batchTimeoutMs);
                    newProcessor.start();
                    return newProcessor;
                });

        // Add to queue - this NEVER blocks (unbounded queue)
        boolean added = processor.addItem(new BatchItem(event, acknowledgment));

        if (!added) {
            log.error("Failed to add item to queue for phoneNumberId={}", phoneNumberId);
            acknowledgment.acknowledge(); // Prevent reprocessing
        }
    }

    /**
     * Graceful shutdown
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down BatchCoordinator...");
        shutdownRequested.set(true);

        for (UserBatchProcessor processor : userProcessors.values()) {
            processor.shutdown();
        }

        log.info("BatchCoordinator shutdown complete");
    }

    // ==================== USER BATCH PROCESSOR ====================

    /**
     * One processor per user. Runs in dedicated thread.
     * Continuously drains queue and processes batches.
     */
    private class UserBatchProcessor {
        private final String phoneNumberId;
        private final int maxBatchSize;
        private final long timeoutMs;
        private final BlockingQueue<BatchItem> queue;
        private final Thread processorThread;
        private final AtomicBoolean running = new AtomicBoolean(false);

        public UserBatchProcessor(String phoneNumberId, int maxBatchSize, long timeoutMs) {
            this.phoneNumberId = phoneNumberId;
            this.maxBatchSize = maxBatchSize;
            this.timeoutMs = timeoutMs;
            this.queue = new LinkedBlockingQueue<>(); // Unbounded - never blocks

            this.processorThread = new Thread(this::processLoop);
            this.processorThread.setName("batch-processor-" + phoneNumberId);
            this.processorThread.setDaemon(false);
        }

        public void start() {
            running.set(true);
            processorThread.start();
            log.info("Started batch processor for phoneNumberId={}", phoneNumberId);
        }

        public boolean addItem(BatchItem item) {
            return queue.offer(item);
        }

        public void shutdown() {
            running.set(false);
            processorThread.interrupt();
        }

        /**
         * Main processing loop - runs continuously in dedicated thread
         */
        private void processLoop() {
            log.info("Batch processor loop started for phoneNumberId={}", phoneNumberId);

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    List<BatchItem> batch = collectBatch();

                    if (!batch.isEmpty()) {
                        processBatch(batch);
                    }

                } catch (InterruptedException e) {
                    log.info("Batch processor interrupted for phoneNumberId={}", phoneNumberId);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in batch processor loop for phoneNumberId={}", phoneNumberId, e);
                }
            }

            // Process remaining items before shutdown
            processRemainingItems();

            log.info("Batch processor loop ended for phoneNumberId={}", phoneNumberId);
        }

        /**
         * Collect items into batch using smart timeout strategy
         */
        private List<BatchItem> collectBatch() throws InterruptedException {
            List<BatchItem> batch = new ArrayList<>();

            // Wait for first item (blocking)
            BatchItem firstItem = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (firstItem == null) {
                return batch; // Timeout, return empty batch
            }

            batch.add(firstItem);

            // Collect more items without blocking (drain up to maxBatchSize)
            queue.drainTo(batch, maxBatchSize - 1);

            return batch;
        }

        /**
         * Process a batch of items
         */
        private void processBatch(List<BatchItem> batch) {
            long startTime = System.currentTimeMillis();

            log.info("=== Processing Batch ===");
            log.info("PhoneNumberId: {} | Size: {} | Queue remaining: {}",
                    phoneNumberId, batch.size(), queue.size());

            try {
                // STAGE 1: WhatsApp API Calls
                List<WhatsAppResult> results = sendWhatsAppBatch(batch);

                // STAGE 2: Database Update
                batchUpdateDatabase(batch, results);

                // STAGE 3: Acknowledge Kafka messages
                acknowledgeAllMessages(batch);

                long duration = System.currentTimeMillis() - startTime;
                log.info("=== Batch Completed ===");
                log.info("PhoneNumberId: {} | Items: {} | Duration: {}ms | Queue: {}",
                        phoneNumberId, batch.size(), duration, queue.size());

            } catch (Exception e) {
                log.error("Batch processing failed for phoneNumberId={}", phoneNumberId, e);
                handleBatchFailure(batch, e);
            }
        }

        /**
         * Process remaining items during shutdown
         */
        private void processRemainingItems() {
            List<BatchItem> remaining = new ArrayList<>();
            queue.drainTo(remaining);

            if (!remaining.isEmpty()) {
                log.info("Processing {} remaining items during shutdown for phoneNumberId={}",
                        remaining.size(), phoneNumberId);
                processBatch(remaining);
            }
        }

        /**
         * STAGE 1: Send WhatsApp requests concurrently
         */
        private List<WhatsAppResult> sendWhatsAppBatch(List<BatchItem> batch) {
            long stageStart = System.currentTimeMillis();

            Semaphore userSemaphore = ExecutorConfig.getSemaphoreForUser(
                    userSemaphores, semaphoreLastUsed, phoneNumberId);

            List<Semaphore> acquiredSemaphores = new ArrayList<>();
            List<CompletableFuture<WhatsAppResult>> futures = new ArrayList<>();

            try {
                log.debug("Stage 1: Acquiring {} permits", batch.size());

                // Acquire permits
                for (int i = 0; i < batch.size(); i++) {
                    userSemaphore.acquire();
                    acquiredSemaphores.add(userSemaphore);
                }

                log.debug("Stage 1: Permits acquired. Available: {}",
                        userSemaphore.availablePermits());

                // Submit concurrent WhatsApp requests
                for (BatchItem item : batch) {
                    CompletableFuture<WhatsAppResult> future = CompletableFuture
                            .supplyAsync(() -> sendSingleWhatsAppMessage(item.event()));
                    futures.add(future);
                }

                // Wait for all responses
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(300, TimeUnit.SECONDS);

                // Collect results
                List<WhatsAppResult> results = new ArrayList<>();
                for (CompletableFuture<WhatsAppResult> future : futures) {
                    results.add(future.get());
                }

                long stageDuration = System.currentTimeMillis() - stageStart;
                log.info("Stage 1: Completed. Duration: {}ms | Success: {}/{}",
                        stageDuration,
                        results.stream().filter(WhatsAppResult::success).count(),
                        results.size());

                return results;

            } catch (Exception e) {
                log.error("Stage 1: Failed for phoneNumberId={}", phoneNumberId, e);

                // Create error results
                List<WhatsAppResult> errorResults = new ArrayList<>();
                for (BatchItem item : batch) {
                    errorResults.add(new WhatsAppResult(
                            item.event().getBroadcastId(),
                            item.event().getRecipient(),
                            null,
                            false,
                            "Batch error: " + e.getMessage()));
                }
                return errorResults;

            } finally {
                // Release all permits
                for (Semaphore sem : acquiredSemaphores) {
                    sem.release();
                }
                log.debug("Stage 1: Released {} permits", acquiredSemaphores.size());
            }
        }

        /**
         * Send single WhatsApp message
         */
        private WhatsAppResult sendSingleWhatsAppMessage(BroadcastReportEvent event) {
            try {
                FacebookApiResponse<SendTemplateMessageResponse> response = whatsappClient.sendMessage(
                        event.getPayload(),
                        event.getPhoneNumberId(),
                        event.getAccessToken());

                return new WhatsAppResult(
                        event.getBroadcastId(),
                        event.getRecipient(),
                        response,
                        response.isSuccess(),
                        null);

            } catch (Exception e) {
                log.error("WhatsApp request failed: recipient={}",
                        event.getRecipient(), e);
                return new WhatsAppResult(
                        event.getBroadcastId(),
                        event.getRecipient(),
                        null,
                        false,
                        e.getMessage());
            }
        }

        /**
         * STAGE 2: Batch database update
         */
        private void batchUpdateDatabase(List<BatchItem> batch, List<WhatsAppResult> results) {
            long stageStart = System.currentTimeMillis();

            List<DatabaseUpdate> updates = new ArrayList<>();

            for (int i = 0; i < batch.size(); i++) {
                BatchItem item = batch.get(i);
                WhatsAppResult result = results.get(i);

                try {
                    String responseJson = objectMapper.writeValueAsString(result.response());
                    String status;
                    String messageStatusValue = "Failed";
                    MessageStatus messageStatus = MessageStatus.FAILED;
                    String whatsappMessageId = null;

                    if (result.success() && result.response() != null) {
                        status = "sent"; // API call succeeded
                        SendTemplateMessageResponse data = result.response().getData();
                        if (data != null && data.getMessages() != null &&
                                !data.getMessages().isEmpty()) {
                            var msg = data.getMessages().get(0);
                            whatsappMessageId = msg.getId();
                            String statusStr = msg.getMessageStatus();
                            messageStatus = MessageStatus.fromValue(
                                    statusStr != null ? statusStr : "sent");
                        } else {
                            messageStatusValue = "sent";
                        }
                    } else {
                        status = "failed"; // API call failed
                        messageStatusValue = result.errorMessage() != null ? result.errorMessage() : "Failed";
                    }

                    updates.add(new DatabaseUpdate(
                            item.event().getBroadcastId(),
                            item.event().getRecipient(),
                            responseJson,
                            status,
                            messageStatusValue,
                            whatsappMessageId,
                            LocalDateTime.now()));

                } catch (Exception e) {
                    log.error("Failed to prepare update: recipient={}",
                            item.event().getRecipient(), e);
                }
            }

            try {
                int successCount = reportService.batchUpdateReports(updates);

                long stageDuration = System.currentTimeMillis() - stageStart;
                log.info("Stage 2: Completed. Duration: {}ms | Updated: {}/{}",
                        stageDuration, successCount, updates.size());

            } catch (Exception e) {
                log.error("Stage 2: Failed", e);
                throw e;
            }
        }

        /**
         * STAGE 3: Acknowledge Kafka messages
         */
        private void acknowledgeAllMessages(List<BatchItem> batch) {
            for (BatchItem item : batch) {
                try {
                    item.acknowledgment().acknowledge();
                } catch (Exception e) {
                    log.error("Failed to acknowledge: recipient={}",
                            item.event().getRecipient(), e);
                }
            }
        }

        /**
         * Handle batch failure
         */
        private void handleBatchFailure(List<BatchItem> batch, Exception error) {
            log.error("Handling batch failure for {} items", batch.size());

            for (BatchItem item : batch) {
                try {
                    item.acknowledgment().acknowledge();
                } catch (Exception e) {
                    log.error("Failed to acknowledge failed message", e);
                }
            }
        }
    }

    // ==================== INNER CLASSES ====================

    private record BatchItem(
            BroadcastReportEvent event,
            Acknowledgment acknowledgment) {
    }

    private record WhatsAppResult(
            Long broadcastId,
            String recipient,
            FacebookApiResponse<SendTemplateMessageResponse> response,
            boolean success,
            String errorMessage) {
    }

    public record DatabaseUpdate(
            Long broadcastId,
            String mobile,
            String responseJson,
            String status,
            String messageStatus,
            String whatsappMessageId,
            LocalDateTime timestamp) {
    }
}