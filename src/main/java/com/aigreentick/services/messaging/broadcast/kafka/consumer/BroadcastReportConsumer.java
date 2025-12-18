package com.aigreentick.services.messaging.broadcast.kafka.consumer;

import java.time.LocalDateTime;
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
import com.aigreentick.services.messaging.config.ExecutorConfig;
import com.aigreentick.services.messaging.report.service.impl.ReportServiceImpl;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    private final ReportServiceImpl reportService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Qualifier("whatsappExecutor")
    private final ExecutorService whatsappExecutor;

    // Track processed event IDs for idempotency (with size limit)
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
                event.getBroadcstId(), event.getRecipient(), userSemaphore.availablePermits());

            try {
                // Process message and get result
                boolean success = processMessage(event);
                
                if (success) {
                    log.debug("Message sent successfully. broadcastId={} recipient={} duration={}ms",
                        event.getBroadcstId(), event.getRecipient(), 
                        System.currentTimeMillis() - startTime);
                } else {
                    log.warn("Message processing failed. broadcastId={} recipient={} duration={}ms",
                        event.getBroadcstId(), event.getRecipient(), 
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
                    event.getBroadcstId(), userSemaphore.availablePermits());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while processing. broadcastId={} eventId={} partition={} offset={}",
                    event.getBroadcstId(), event.getEventId(), partition, offset, e);
            // Don't acknowledge - message will be redelivered

        } catch (Exception e) {
            log.error("Critical error processing message. broadcastId={} eventId={} partition={} offset={}",
                    event.getBroadcstId(), event.getEventId(), partition, offset, e);
            
            // Send to DLQ for manual inspection
            sendToDLQ(event, e);
            
            // Acknowledge to prevent infinite retry loop
            acknowledgment.acknowledge();
        }
    }

    /**
     * Core business logic: Send WhatsApp message and update database
     */
    private boolean processMessage(BroadcastReportEvent event) {
        try {
            // 1. Serialize template payload
            String payload = objectMapper
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(event.getBuildTemplateReqest());

            log.debug("Sending WhatsApp message. recipient={} templateName={}", 
                event.getRecipient(), 
                event.getBuildTemplateReqest().getTemplate().getName());

            // 2. Call WhatsApp API
            FacebookApiResponse<SendTemplateMessageResponse> response = whatsappClient.sendMessage(
                    payload,
                    event.getPhoneNumberId(),
                    event.getAccessToken());

            // 3. Update database with result
            boolean success = updateReportMessage(event, response, payload);

            return success;

        } catch (Exception e) {
            log.error("Error in message processing. broadcastId={} recipient={} error={}",
                    event.getBroadcstId(), event.getRecipient(), e.getMessage(), e);
            
            // Update database with failure status
            try {
                updateReportMessageWithError(event, e);
            } catch (Exception dbError) {
                log.error("Failed to update database with error status. reportId={}", 
                    event.getBroadcastReportId(), dbError);
            }
            
            return false;
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
            // Remove expired entry
            processedEventIds.remove(eventId);
        }
        return false;
    }

    /**
     * Mark event as processed and cleanup if cache too large
     */
    private void markAsProcessed(String eventId) {
        processedEventIds.put(eventId, System.currentTimeMillis());

        // Prevent memory leak - cleanup when cache grows too large
        if (processedEventIds.size() > MAX_IDEMPOTENCY_CACHE_SIZE) {
            cleanupOldEventIds();
        }
    }

    /**
     * Updates the Report entity with WhatsApp API response
     */
    private boolean updateReportMessage(
            BroadcastReportEvent event,
            FacebookApiResponse<SendTemplateMessageResponse> response,
            String payload) {

        try {
            // Serialize response
            String responseJson = objectMapper.writeValueAsString(response);

            boolean success = false;
            String whatsappMessageId = null;
            MessageStatus status = MessageStatus.FAILED;

            // Parse WhatsApp response
            if (response.isSuccess() && response.getData() != null) {
                SendTemplateMessageResponse data = response.getData();

                if (data.getMessages() != null && !data.getMessages().isEmpty()) {
                    var msg = data.getMessages().get(0);

                    whatsappMessageId = msg.getId();
                    status = MessageStatus.fromValue(
                            msg.getMessageStatus() != null ? msg.getMessageStatus() : "accepted");
                    success = true;
                }
            } else {
                log.warn("WhatsApp API returned error. reportId={} error={}", 
                    event.getBroadcastReportId(), response.getErrorMessage());
            }

            // Update database using optimized query
            int updated = reportService.updateReportMessage(
                    event.getBroadcastReportId(),
                    payload,
                    responseJson,
                    status,
                    whatsappMessageId,
                    LocalDateTime.now());

            if (updated == 0) {
                log.error("Failed to update report - no rows affected. reportId={}", 
                    event.getBroadcastReportId());
                return false;
            }

            return updated > 0 && success;

        } catch (Exception e) {
            log.error("Failed to update report message. reportId={}", 
                event.getBroadcastReportId(), e);
            return false;
        }
    }

    /**
     * Update report with error information when WhatsApp call fails
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

            reportService.updateReportMessage(
                event.getBroadcastReportId(),
                null, // payload
                errorResponse,
                MessageStatus.FAILED,
                null, // messageId
                LocalDateTime.now()
            );

        } catch (Exception e) {
            log.error("Failed to update report with error. reportId={}", 
                event.getBroadcastReportId(), e);
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
                String.valueOf(event.getBroadcstId()), 
                dlqMessage);

            log.info("Sent message to DLQ. broadcastId={} eventId={}", 
                event.getBroadcstId(), event.getEventId());

        } catch (Exception e) {
            log.error("Failed to send message to DLQ. broadcastId={} eventId={}", 
                event.getBroadcstId(), event.getEventId(), e);
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
            if (sb.length() > 2000) break; // Limit size
        }
        return sb.toString();
    }
}