package com.aigreentick.services.messaging.broadcast.kafka.consumer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.aigreentick.services.messaging.broadcast.client.dto.FacebookApiResponse;
import com.aigreentick.services.messaging.broadcast.client.service.impl.WhatsappClient;
import com.aigreentick.services.messaging.broadcast.dto.response.SendTemplateMessageResponse;
import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;
import com.aigreentick.services.messaging.broadcast.kafka.event.BroadcastReportEvent;
import com.aigreentick.services.messaging.broadcast.service.impl.BatchUpdateService;
import com.aigreentick.services.messaging.config.ExecutorConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastReportConsumer {

    private final ConcurrentHashMap<String, Semaphore> userSemaphores;
    private final ConcurrentHashMap<String, Long> semaphoreLastUsed;
    private final ObjectMapper objectMapper;
    private final WhatsappClient whatsappClient;
    private final BatchUpdateService batchUpdateService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Qualifier("whatsappExecutor")
    private final ExecutorService whatsappExecutor;

    // Track processed event IDs for idempotency
    private final Map<String, Long> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_WINDOW_MS = 3600000; // 1 hour
    private static final int MAX_IDEMPOTENCY_CACHE_SIZE = 10000;

    @KafkaListener(
        topics = "${kafka.topics.campaign-messages.name}", 
        groupId = "${spring.kafka.consumer.group-id}", 
        containerFactory = "campaignKafkaListenerFactory"
    )
    public void consumeCampaignMessage(
            @Payload BroadcastReportEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        // Submit to executor immediately - DON'T BLOCK consumer thread
        whatsappExecutor.submit(() -> {
            processMessageAsync(event, partition, offset, acknowledgment);
        });
    }

    private void processMessageAsync(BroadcastReportEvent event, int partition, long offset,
            Acknowledgment acknowledgment) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Idempotency check
            if (isDuplicate(event.getEventId())) {
                log.debug("Duplicate event detected, skipping. eventId={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }

            // Get semaphore for THIS user's phoneNumberId
            Semaphore userSemaphore = ExecutorConfig.getSemaphoreForUser(
                    userSemaphores,
                    semaphoreLastUsed,
                    event.getPhoneNumberId());

            // Acquire permit (blocks if all 80 permits are in use)
            userSemaphore.acquire();
            
            log.debug("Acquired semaphore. broadcastId={} recipient={} availablePermits={}", 
                event.getBroadcastId(), event.getRecipient(), userSemaphore.availablePermits());

            try {
                // Process message - this will WAIT until batch is complete
                boolean success = processMessage(event);
                
                if (success) {
                    log.debug("Message processed successfully. broadcastId={} recipient={} duration={}ms",
                        event.getBroadcastId(), event.getRecipient(), 
                        System.currentTimeMillis() - startTime);
                } else {
                    log.warn("Message processing failed. broadcastId={} recipient={} duration={}ms",
                        event.getBroadcastId(), event.getRecipient(), 
                        System.currentTimeMillis() - startTime);
                }

                // Mark as processed for idempotency
                markAsProcessed(event.getEventId());

                // Acknowledge successful processing
                acknowledgment.acknowledge();

            } finally {
                // CRITICAL: Always release semaphore
                userSemaphore.release();
                log.debug("Released semaphore. broadcastId={} availablePermits={}", 
                    event.getBroadcastId(), userSemaphore.availablePermits());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while processing. broadcastId={} eventId={} partition={} offset={}",
                    event.getBroadcastId(), event.getEventId(), partition, offset, e);
            // Don't acknowledge - message will be redelivered

        } catch (Exception e) {
            log.error("Critical error processing message. broadcastId={} eventId={} partition={} offset={}",
                    event.getBroadcastId(), event.getEventId(), partition, offset, e);
            
            // Send to DLQ for manual inspection
            sendToDLQ(event, e);
            
            // Acknowledge to prevent infinite retry loop
            acknowledgment.acknowledge();
        }
    }

    /**
     * Core business logic: Send WhatsApp message and batch update database
     */
    private boolean processMessage(BroadcastReportEvent event) {
        try {
            // Validate payload exists
            if (event.getPayload() == null || event.getPayload().isEmpty()) {
                log.error("Event has no payload. eventId={}", event.getEventId());
                return false;
            }

            log.debug("Sending WhatsApp message. recipient={}", event.getRecipient());

            // Call WhatsApp API with pre-built payload
            FacebookApiResponse<SendTemplateMessageResponse> response = whatsappClient.sendMessage(
                    event.getPayload(),
                    event.getPhoneNumberId(),
                    event.getAccessToken());

            // Add to batch and WAIT for batch processing
            boolean success = addToBatchUpdate(event, response);

            return success;

        } catch (Exception e) {
            log.error("Error in message processing. broadcastId={} recipient={} error={}",
                    event.getBroadcastId(), event.getRecipient(), e.getMessage(), e);
            
            // Update database with failure status
            try {
                updateReportMessageWithError(event, e);
            } catch (Exception dbError) {
                log.error("Failed to update database with error status. broadcastId={} mobile={}", 
                    event.getBroadcastId(), event.getRecipient(), dbError);
            }
            
            return false;
        }
    }

    /**
     * Add update to batch and WAIT for batch to complete
     */
    private boolean addToBatchUpdate(
            BroadcastReportEvent event,
            FacebookApiResponse<SendTemplateMessageResponse> response) throws InterruptedException {

        try {
            // Serialize response
            String responseJson = objectMapper.writeValueAsString(response);

            boolean success = false;
            String whatsappMessageId = null;
            MessageStatus messageStatus = MessageStatus.FAILED;

            // Parse WhatsApp response
            if (response.isSuccess() && response.getData() != null) {
                SendTemplateMessageResponse data = response.getData();

                if (data.getMessages() != null && !data.getMessages().isEmpty()) {
                    var msg = data.getMessages().get(0);

                    whatsappMessageId = msg.getId();
                    
                    // Parse message status from response
                    String statusStr = msg.getMessageStatus();
                    messageStatus = MessageStatus.fromValue(statusStr != null ? statusStr : "accepted");
                    
                    success = true;
                }
            } else {
                log.warn("WhatsApp API returned error. broadcastId={} mobile={} error={}", 
                    event.getBroadcastId(), event.getRecipient(), response.getErrorMessage());
            }

            // Add to batch - THIS BLOCKS until batch is processed
            batchUpdateService.addUpdateAndWait(
                    event.getBroadcastId(),
                    event.getRecipient(),
                    responseJson,
                    messageStatus,
                    whatsappMessageId);

            return success;

        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to add to batch update. broadcastId={} mobile={}", 
                event.getBroadcastId(), event.getRecipient(), e);
            return false;
        }
    }

    /**
     * Update report with error (direct update, not batched)
     */
    private void updateReportMessageWithError(BroadcastReportEvent event, Exception error) {
        try {
            String errorResponse = objectMapper.writeValueAsString(
                Map.of(
                    "error", error.getMessage(),
                    "errorType", error.getClass().getSimpleName(),
                    "timestamp", System.currentTimeMillis()
                )
            );

            batchUpdateService.addUpdateAndWait(
                event.getBroadcastId(),
                event.getRecipient(),
                errorResponse,
                MessageStatus.FAILED,
                null
            );

        } catch (Exception e) {
            log.error("Failed to update report with error. broadcastId={} mobile={}", 
                event.getBroadcastId(), event.getRecipient(), e);
        }
    }

    /**
     * Send failed messages to Dead Letter Queue for manual review
     */
    private void sendToDLQ(BroadcastReportEvent event, Exception error) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalEvent", event,
                "errorMessage", error.getMessage(),
                "errorType", error.getClass().getSimpleName(),
                "timestamp", System.currentTimeMillis(),
                "stackTrace", getStackTraceAsString(error)
            );

            kafkaTemplate.send("broadcast-messages-dlq", 
                String.valueOf(event.getBroadcastId()), 
                dlqMessage);

            log.info("Sent message to DLQ. broadcastId={} eventId={}", 
                event.getBroadcastId(), event.getEventId());

        } catch (Exception e) {
            log.error("Failed to send message to DLQ. broadcastId={} eventId={}", 
                event.getBroadcastId(), event.getEventId(), e);
        }
    }

    /**
     * Check for duplicate events using in-memory cache
     */
    private boolean isDuplicate(String eventId) {
        Long lastProcessed = processedEventIds.get(eventId);
        if (lastProcessed != null) {
            long age = System.currentTimeMillis() - lastProcessed;
            if (age < IDEMPOTENCY_WINDOW_MS) {
                return true;
            }
            processedEventIds.remove(eventId);
        }
        return false;
    }

    /**
     * Mark event as processed and cleanup if cache too large
     */
    private void markAsProcessed(String eventId) {
        processedEventIds.put(eventId, System.currentTimeMillis());

        if (processedEventIds.size() > MAX_IDEMPOTENCY_CACHE_SIZE) {
            cleanupOldEventIds();
        }
    }

    /**
     * Cleanup old event IDs to prevent memory leak
     */
    private void cleanupOldEventIds() {
        long cutoff = System.currentTimeMillis() - IDEMPOTENCY_WINDOW_MS;
        int initialSize = processedEventIds.size();
        
        processedEventIds.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        
        int removed = initialSize - processedEventIds.size();
        if (removed > 0) {
            log.info("Cleaned up {} old event IDs. Remaining: {}", removed, processedEventIds.size());
        }
    }

    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
            if (sb.length() > 2000) break;
        }
        return sb.toString();
    }
}