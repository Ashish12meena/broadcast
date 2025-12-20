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

    // Per-user batch storage by phoneNumbereId
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

        // Add event to batch
        batch.addEvent(new BatchItem(event, acknowledgment));

        // Check if batch is ready to process
        if (batch.shouldProcess()) {
            processBatchAsync(phoneNumberId, batch);
        }
    }

    /**
     * Process batch asynchronously in separate thread.
     * This prevents blocking the Kafka consumer thread.
     */
    private void processBatchAsync(String phoneNumberId, UserBatch batch) {
        CompletableFuture.runAsync(() -> {
            try {
                processBatch(phoneNumberId, batch);
            } catch (Exception e) {
                log.error("Failed to process batch for phoneNumberId={}", phoneNumberId, e);
            }
        });
    }

    /**
     * Main batch processing logic - TWO STAGE approach.
     */
    private void processBatch(String phoneNumberId, UserBatch batch) {
        List<BatchItem> items = batch.claimBatchForProcessing();
        
        if (items.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("=== Processing Batch for phoneNumberId={} ===", phoneNumberId);
        log.info("Batch size: {}", items.size());

        try {
            // STAGE 1: WhatsApp API Calls (with semaphore)
            List<WhatsAppResult> whatsappResults = sendWhatsAppBatch(phoneNumberId, items);
            
            // STAGE 2: Database Update (single transaction)
            batchUpdateDatabase(items, whatsappResults);
            
            // STAGE 3: Acknowledge Kafka messages
            acknowledgeAllMessages(items);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("=== Batch Completed Successfully ===");
            log.info("phoneNumberId: {} | Items: {} | Duration: {}ms", 
                phoneNumberId, items.size(), duration);

        } catch (Exception e) {
            log.error("Critical error processing batch for phoneNumberId={}", phoneNumberId, e);
            handleBatchFailure(items, e);
        } finally {
            // Mark batch as completed and cleanup if needed
            batch.markCompleted();
            cleanupCompletedBatch(phoneNumberId, batch);
        }
    }

    /**
     * STAGE 1: Send all WhatsApp requests concurrently.
     * Acquires semaphores, sends requests, waits for all responses, releases semaphores.
     */
    private List<WhatsAppResult> sendWhatsAppBatch(String phoneNumberId, List<BatchItem> items) {
        long stageStart = System.currentTimeMillis();
        
        // Get semaphore for this user
        Semaphore userSemaphore = ExecutorConfig.getSemaphoreForUser(
            userSemaphores, semaphoreLastUsed, phoneNumberId);

        List<Semaphore> acquiredSemaphores = new ArrayList<>();
        List<CompletableFuture<WhatsAppResult>> futures = new ArrayList<>();

        try {
            log.info("Stage 1: Acquiring {} semaphore permits for phoneNumberId={}", 
                items.size(), phoneNumberId);

            // Acquire semaphore permits for all items
            for (int i = 0; i < items.size(); i++) {
                userSemaphore.acquire();
                acquiredSemaphores.add(userSemaphore);
            }

            log.info("Stage 1: All permits acquired. Available: {}", userSemaphore.availablePermits());

            // Send all WhatsApp requests CONCURRENTLY
            for (BatchItem item : items) {
                CompletableFuture<WhatsAppResult> future = CompletableFuture.supplyAsync(() -> 
                    sendSingleWhatsAppMessage(item.event())
                );
                futures.add(future);
            }

            log.info("Stage 1: Submitted {} concurrent WhatsApp requests", futures.size());

            // Wait for ALL responses to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );

            // Wait with timeout
            allFutures.get(300, TimeUnit.SECONDS);

            // Collect all results
            List<WhatsAppResult> results = new ArrayList<>();
            for (CompletableFuture<WhatsAppResult> future : futures) {
                results.add(future.get());
            }

            long stageDuration = System.currentTimeMillis() - stageStart;
            log.info("Stage 1: WhatsApp batch completed. Duration: {}ms | Success rate: {}/{}", 
                stageDuration, 
                results.stream().filter(WhatsAppResult::success).count(),
                results.size());

            return results;

        } catch (Exception e) {
            log.error("Stage 1: WhatsApp batch failed for phoneNumberId={}", phoneNumberId, e);
            
            // Create error results for all items
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
            //  Release ALL semaphore permits
            for (Semaphore sem : acquiredSemaphores) {
                sem.release();
            }
            log.info("Stage 1: Released {} semaphore permits. Available: {}", 
                acquiredSemaphores.size(), userSemaphore.availablePermits());
        }
    }

    /**
     * Send single WhatsApp message and return result.
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
     * STAGE 2: Batch update database in SINGLE transaction.
     * All semaphores already released - no blocking WhatsApp calls.
     */
    private void batchUpdateDatabase(List<BatchItem> items, List<WhatsAppResult> results) {
        long stageStart = System.currentTimeMillis();
        
        log.info("Stage 2: Starting database batch update for {} items", items.size());

        List<DatabaseUpdate> updates = new ArrayList<>();

        // Prepare all updates
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
                log.error("Failed to prepare update for recipient={}", 
                    item.event().getRecipient(), e);
            }
        }

        // Execute batch update in single transaction
        try {
            int successCount = reportService.batchUpdateReports(updates);
            
            long stageDuration = System.currentTimeMillis() - stageStart;
            log.info("Stage 2: Database batch update completed. Duration: {}ms | Updated: {}/{}", 
                stageDuration, successCount, updates.size());

        } catch (Exception e) {
            log.error("Stage 2: Database batch update failed", e);
            throw e;
        }
    }

    /**
     * STAGE 3: Acknowledge all Kafka messages.
     */
    private void acknowledgeAllMessages(List<BatchItem> items) {
        log.info("Stage 3: Acknowledging {} Kafka messages", items.size());
        
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
        
        // Acknowledge messages to prevent reprocessing loop
        for (BatchItem item : items) {
            try {
                item.acknowledgment().acknowledge();
            } catch (Exception e) {
                log.error("Failed to acknowledge failed message", e);
            }
        }
    }

    /**
     * Cleanup completed batch if no pending items.
     */
    private void cleanupCompletedBatch(String phoneNumberId, UserBatch batch) {
        if (batch.isEmpty() && batch.isCompleted()) {
            userBatches.remove(phoneNumberId);
            log.debug("Cleaned up completed batch for phoneNumberId={}", phoneNumberId);
        }
    }

    // ==================== INNER CLASSES ====================

    /**
     * Holds batch of events for a single user.
     */
    private static class UserBatch {
        private final String phoneNumberId;
        private final int maxSize;
        private final long timeoutMs;
        private final long creationTime;
        private final List<BatchItem> items = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();
        private boolean processing = false;
        private boolean completed = false;

        public UserBatch(String phoneNumberId, int maxSize, long timeoutMs) {
            this.phoneNumberId = phoneNumberId;
            this.maxSize = maxSize;
            this.timeoutMs = timeoutMs;
            this.creationTime = System.currentTimeMillis();
        }

        public void addEvent(BatchItem item) {
            lock.lock();
            try {
                if (!processing && !completed) {
                    items.add(item);
                }
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

    /**
     * Batch item with event and acknowledgment.
     */
    private record BatchItem(
        BroadcastReportEvent event,
        Acknowledgment acknowledgment
    ) {}

    /**
     * WhatsApp API call result.
     */
    private record WhatsAppResult(
        Long broadcastId,
        String recipient,
        FacebookApiResponse<SendTemplateMessageResponse> response,
        boolean success,
        String errorMessage
    ) {}

    /**
     * Database update request.
     */
    public record DatabaseUpdate(
        Long broadcastId,
        String mobile,
        String responseJson,
        MessageStatus messageStatus,
        String whatsappMessageId,
        LocalDateTime timestamp
    ) {}
}