package com.aigreentick.services.messaging.broadcast.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
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

    // Per-user batch storage by phoneNumberId
    private final ConcurrentHashMap<String, UserBatch> userBatches = new ConcurrentHashMap<>();

    /**
     * Add event to batch for processing.
     * Returns immediately - processing happens when batch is full or timeout occurs.
     */
    public void addEventToBatch(BroadcastReportEvent event, Acknowledgment acknowledgment) {
        String phoneNumberId = event.getPhoneNumberId();

        // Get or create batch for this user
        UserBatch batch = userBatches.computeIfAbsent(phoneNumberId, 
            k -> new UserBatch(phoneNumberId, batchSize, batchTimeoutMs));

        // Add event to batch (now handles overflow properly)
        boolean added = batch.addEvent(new BatchItem(event, acknowledgment));
        
        if (!added) {
            // Create overflow batch if current batch is processing
            log.debug("Current batch processing, creating overflow batch for phoneNumberId={}", phoneNumberId);
            UserBatch overflowBatch = new UserBatch(phoneNumberId, batchSize, batchTimeoutMs);
            overflowBatch.addEvent(new BatchItem(event, acknowledgment));
            
            // Try to register overflow batch
            userBatches.put(phoneNumberId + ":overflow", overflowBatch);
            
            // Check if overflow batch should process
            if (overflowBatch.shouldProcess()) {
                processBatchAsync(phoneNumberId + ":overflow", overflowBatch);
            }
            return;
        }

        // Check if batch is ready to process
        if (batch.shouldProcess()) {
            processBatchAsync(phoneNumberId, batch);
        }
    }

    /**
     * Process batch asynchronously in separate thread.
     * This prevents blocking the Kafka consumer thread.
     */
    private void processBatchAsync(String batchKey, UserBatch batch) {
        CompletableFuture.runAsync(() -> {
            try {
                processBatch(batchKey, batch);
            } catch (Exception e) {
                log.error("Failed to process batch for batchKey={}", batchKey, e);
            }
        });
    }

    /**
     * Main batch processing logic - TWO STAGE approach.
     */
    private void processBatch(String batchKey, UserBatch batch) {
        List<BatchItem> items = batch.claimBatchForProcessing();
        
        if (items.isEmpty()) {
            return;
        }

        String phoneNumberId = batch.getPhoneNumberId();
        long startTime = System.currentTimeMillis();
        
        log.info("=== Processing Batch ===");
        log.info("BatchKey: {} | PhoneNumberId: {} | Size: {}", batchKey, phoneNumberId, items.size());

        try {
            // STAGE 1: WhatsApp API Calls (with semaphore)
            List<WhatsAppResult> whatsappResults = sendWhatsAppBatch(phoneNumberId, items);
            
            // STAGE 2: Database Update (single transaction)
            batchUpdateDatabase(items, whatsappResults);
            
            // STAGE 3: Acknowledge Kafka messages
            acknowledgeAllMessages(items);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("=== Batch Completed ===");
            log.info("BatchKey: {} | Items: {} | Duration: {}ms", batchKey, items.size(), duration);

        } catch (Exception e) {
            log.error("Critical error processing batch for batchKey={}", batchKey, e);
            handleBatchFailure(items, e);
        } finally {
            // Mark batch as completed and cleanup
            batch.markCompleted();
            cleanupCompletedBatch(batchKey, batch);
        }
    }

    /**
     * STAGE 1: Send all WhatsApp requests concurrently.
     */
    private List<WhatsAppResult> sendWhatsAppBatch(String phoneNumberId, List<BatchItem> items) {
        long stageStart = System.currentTimeMillis();
        
        Semaphore userSemaphore = ExecutorConfig.getSemaphoreForUser(
            userSemaphores, semaphoreLastUsed, phoneNumberId);

        List<Semaphore> acquiredSemaphores = new ArrayList<>();
        List<CompletableFuture<WhatsAppResult>> futures = new ArrayList<>();

        try {
            log.debug("Stage 1: Acquiring {} permits for phoneNumberId={}", items.size(), phoneNumberId);

            // Acquire semaphore permits
            for (int i = 0; i < items.size(); i++) {
                userSemaphore.acquire();
                acquiredSemaphores.add(userSemaphore);
            }

            log.debug("Stage 1: All permits acquired. Available: {}", userSemaphore.availablePermits());

            // Send all WhatsApp requests concurrently
            for (BatchItem item : items) {
                CompletableFuture<WhatsAppResult> future = CompletableFuture.supplyAsync(() -> 
                    sendSingleWhatsAppMessage(item.event())
                );
                futures.add(future);
            }

            log.debug("Stage 1: Submitted {} concurrent WhatsApp requests", futures.size());

            // Wait for all responses
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(300, TimeUnit.SECONDS);

            // Collect results
            List<WhatsAppResult> results = new ArrayList<>();
            for (CompletableFuture<WhatsAppResult> future : futures) {
                results.add(future.get());
            }

            long stageDuration = System.currentTimeMillis() - stageStart;
            log.info("Stage 1: WhatsApp batch completed. Duration: {}ms | Success: {}/{}", 
                stageDuration, 
                results.stream().filter(WhatsAppResult::success).count(),
                results.size());

            return results;

        } catch (Exception e) {
            log.error("Stage 1: WhatsApp batch failed for phoneNumberId={}", phoneNumberId, e);
            
            List<WhatsAppResult> errorResults = new ArrayList<>();
            for (BatchItem item : items) {
                errorResults.add(new WhatsAppResult(
                    item.event().getBroadcastId(),
                    item.event().getRecipient(),
                    null,
                    false,
                    "Batch processing error: " + e.getMessage()
                ));
            }
            return errorResults;

        } finally {
            for (Semaphore sem : acquiredSemaphores) {
                sem.release();
            }
            log.debug("Stage 1: Released {} permits. Available: {}", 
                acquiredSemaphores.size(), userSemaphore.availablePermits());
        }
    }

    /**
     * Send single WhatsApp message.
     */
    private WhatsAppResult sendSingleWhatsAppMessage(BroadcastReportEvent event) {
        try {
            FacebookApiResponse<SendTemplateMessageResponse> response = whatsappClient.sendMessage(
                event.getPayload(),
                event.getPhoneNumberId(),
                event.getAccessToken()
            );

            return new WhatsAppResult(
                event.getBroadcastId(),
                event.getRecipient(),
                response,
                response.isSuccess(),
                null
            );

        } catch (Exception e) {
            log.error("WhatsApp request failed for recipient={}", event.getRecipient(), e);
            return new WhatsAppResult(
                event.getBroadcastId(),
                event.getRecipient(),
                null,
                false,
                e.getMessage()
            );
        }
    }

    /**
     * STAGE 2: Batch update database.
     */
    private void batchUpdateDatabase(List<BatchItem> items, List<WhatsAppResult> results) {
        long stageStart = System.currentTimeMillis();
        
        log.debug("Stage 2: Starting database batch update for {} items", items.size());

        List<DatabaseUpdate> updates = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            BatchItem item = items.get(i);
            WhatsAppResult result = results.get(i);

            try {
                String responseJson = objectMapper.writeValueAsString(result.response());
                MessageStatus messageStatus = MessageStatus.FAILED;
                String whatsappMessageId = null;

                if (result.success() && result.response() != null) {
                    SendTemplateMessageResponse data = result.response().getData();
                    if (data != null && data.getMessages() != null && !data.getMessages().isEmpty()) {
                        var msg = data.getMessages().get(0);
                        whatsappMessageId = msg.getId();
                        String statusStr = msg.getMessageStatus();
                        messageStatus = MessageStatus.fromValue(statusStr != null ? statusStr : "accepted");
                    }
                }

                updates.add(new DatabaseUpdate(
                    item.event().getBroadcastId(),
                    item.event().getRecipient(),
                    responseJson,
                    messageStatus,
                    whatsappMessageId,
                    LocalDateTime.now()
                ));

            } catch (Exception e) {
                log.error("Failed to prepare update for recipient={}", item.event().getRecipient(), e);
            }
        }

        try {
            int successCount = reportService.batchUpdateReports(updates);
            
            long stageDuration = System.currentTimeMillis() - stageStart;
            log.info("Stage 2: Database batch completed. Duration: {}ms | Updated: {}/{}", 
                stageDuration, successCount, updates.size());

        } catch (Exception e) {
            log.error("Stage 2: Database batch update failed", e);
            throw e;
        }
    }

    /**
     * STAGE 3: Acknowledge Kafka messages.
     */
    private void acknowledgeAllMessages(List<BatchItem> items) {
        log.debug("Stage 3: Acknowledging {} Kafka messages", items.size());
        
        for (BatchItem item : items) {
            try {
                item.acknowledgment().acknowledge();
            } catch (Exception e) {
                log.error("Failed to acknowledge message for recipient={}", 
                    item.event().getRecipient(), e);
            }
        }
    }

    /**
     * Handle batch processing failure.
     */
    private void handleBatchFailure(List<BatchItem> items, Exception error) {
        log.error("Handling batch failure for {} items", items.size());
        
        for (BatchItem item : items) {
            try {
                item.acknowledgment().acknowledge();
            } catch (Exception e) {
                log.error("Failed to acknowledge failed message", e);
            }
        }
    }

    /**
     * Cleanup completed batch.
     */
    private void cleanupCompletedBatch(String batchKey, UserBatch batch) {
        if (batch.isEmpty() && batch.isCompleted()) {
            userBatches.remove(batchKey);
            log.debug("Cleaned up completed batch: {}", batchKey);
        }
    }

    // ==================== INNER CLASSES ====================

    /**
     * FIXED: UserBatch now properly handles concurrent additions during processing.
     */
    private static class UserBatch {
        private final String phoneNumberId;
        private final int maxSize;
        private final long timeoutMs;
        private final long creationTime;
        private final List<BatchItem> items = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();
        private volatile boolean processing = false;
        private volatile boolean completed = false;

        public UserBatch(String phoneNumberId, int maxSize, long timeoutMs) {
            this.phoneNumberId = phoneNumberId;
            this.maxSize = maxSize;
            this.timeoutMs = timeoutMs;
            this.creationTime = System.currentTimeMillis();
        }

        public String getPhoneNumberId() {
            return phoneNumberId;
        }

        /**
         * FIXED: Returns false if batch is processing instead of silently dropping.
         */
        public boolean addEvent(BatchItem item) {
            lock.lock();
            try {
                if (processing || completed) {
                    return false; // Signal caller to create overflow batch
                }
                
                items.add(item);
                return true;
                
            } finally {
                lock.unlock();
            }
        }

        public boolean shouldProcess() {
            lock.lock();
            try {
                if (processing || completed || items.isEmpty()) {
                    return false;
                }

                boolean isFull = items.size() >= maxSize;
                boolean isTimeout = (System.currentTimeMillis() - creationTime) >= timeoutMs;

                if (isFull || isTimeout) {
                    processing = true;
                    return true;
                }

                return false;
            } finally {
                lock.unlock();
            }
        }

        public List<BatchItem> claimBatchForProcessing() {
            lock.lock();
            try {
                if (!processing) {
                    return new ArrayList<>();
                }
                return new ArrayList<>(items);
            } finally {
                lock.unlock();
            }
        }

        public void markCompleted() {
            lock.lock();
            try {
                completed = true;
                items.clear();
            } finally {
                lock.unlock();
            }
        }

        public boolean isEmpty() {
            lock.lock();
            try {
                return items.isEmpty();
            } finally {
                lock.unlock();
            }
        }

        public boolean isCompleted() {
            return completed;
        }
    }

    private record BatchItem(
        BroadcastReportEvent event,
        Acknowledgment acknowledgment
    ) {}

    private record WhatsAppResult(
        Long broadcastId,
        String recipient,
        FacebookApiResponse<SendTemplateMessageResponse> response,
        boolean success,
        String errorMessage
    ) {}

    public record DatabaseUpdate(
        Long broadcastId,
        String mobile,
        String responseJson,
        MessageStatus messageStatus,
        String whatsappMessageId,
        LocalDateTime timestamp
    ) {}
}