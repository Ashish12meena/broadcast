package com.aigreentick.services.messaging.broadcast.kafka.consumer;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.aigreentick.services.messaging.broadcast.client.dto.FacebookApiResponse;
import com.aigreentick.services.messaging.broadcast.client.service.WhatsappClient;
import com.aigreentick.services.messaging.broadcast.dto.response.SendTemplateMessageResponse;
import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;
import com.aigreentick.services.messaging.broadcast.kafka.event.BroadcastReportEvent;
import com.aigreentick.services.messaging.broadcast.model.ContactMessage;
import com.aigreentick.services.messaging.broadcast.service.impl.ContactMessagesServiceImpl;
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
    private final ContactMessagesServiceImpl contactMessagesService;

    @Qualifier("whatsappExecutor")
    private final ExecutorService whatsappExecutor;

    // Track processed event IDs for idempotency
    private final Map<String, Long> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_WINDOW_MS = 3600000; // 1 hour

    @KafkaListener(topics = "${kafka.topics.campaign-messages.name}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "campaignKafkaListenerFactory")
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
        try {
            // Idempotency check
            if (isDuplicate(event.getEventId())) {
                acknowledgment.acknowledge();
                return;
            }

            // Get semaphore for THIS user's phoneNumberId
            Semaphore userSemaphore = ExecutorConfig.getSemaphoreForUser(
                    userSemaphores,
                    semaphoreLastUsed,
                    event.getPhoneNumberId());

            userSemaphore.acquire();

            try {
                // Process message and get result
                boolean success = processMessage(event);

                // Create ContactMessages entry
                createContactMessageEntry(event);

                // updateCampaignProgressViaAggregator(
                // event.getCampaignId(),
                // event.getProjectId(),
                // success);

                // Mark as processed
                markAsProcessed(event.getEventId());

                // Acknowledge successful processing
                acknowledgment.acknowledge();

            } finally {
                userSemaphore.release();
                acknowledgment.acknowledge();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while processing. campaignId={} eventId={}",
                    event.getBroadcastReportId(), event.getEventId(), e);
            // Don't acknowledge - message will be redelivered

        } catch (Exception e) {
            log.error("Error processing campaign message. campaignId={} eventId={}",
                    event.getBroadcastReportId(), event.getEventId(), e);
            acknowledgment.acknowledge();
        }
    }

    private boolean processMessage(BroadcastReportEvent event) {
        try {
            String payload = objectMapper
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(event.getBuildTemplateReqest());

            FacebookApiResponse<SendTemplateMessageResponse> response = whatsappClient.sendMessage(
                    payload,
                    event.getPhoneNumberId(),
                    event.getAccessToken());

            boolean success = updateReportMessage(event, response, payload);

            return success;

        } catch (Exception e) {
            log.error("Error sending message. campaignId={} recipient={}",
                    event.getBroadcstId(), event.getRecipient(), e);
            return false;
        }
    }

    /**
     * Check for duplicate events
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
     * Updates the CampaignMessages entity with result
     */
    private boolean updateReportMessage(
            BroadcastReportEvent event,
            FacebookApiResponse<SendTemplateMessageResponse> response,
            String payload) {

        try {
            // Serialize response once
            String responseJson = objectMapper.writeValueAsString(response);

            boolean success = false;
            String whatsappMessageId = null;
            MessageStatus status = MessageStatus.FAILED;

            if (response.isSuccess() && response.getData() != null) {
                SendTemplateMessageResponse data = response.getData();

                if (data.getMessages() != null && !data.getMessages().isEmpty()) {
                    var msg = data.getMessages().get(0);

                    whatsappMessageId = msg.getId();
                    status = MessageStatus.fromValue(
                            msg.getMessageStatus() != null
                                    ? msg.getMessageStatus()
                                    : "accepted");
                    success = true;
                }
            }

            int updated = reportService.updateReportMessage(
                    event.getBroadcastReportId(),
                    payload,
                    responseJson,
                    status,
                    whatsappMessageId, // null-safe via COALESCE
                    LocalDateTime.now());

            return updated > 0 && success;

        } catch (Exception e) {
            log.error("Failed to update report. id={}", event.getBroadcastReportId(), e);
            return false;
        }
    }

    /**
     * Creates ContactMessages entry
     */
    private void createContactMessageEntry(BroadcastReportEvent event) {
        try {
            ContactMessage contactMessage = ContactMessage.builder()
                    .chatId(null)
                    .reportId(event.getBroadcastReportId())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            contactMessagesService.save(contactMessage);
            // log.debug("Created ContactMessages entry. campaignMessageId={}",
            // event.getCampaignMessageId());

        } catch (Exception e) {
            // log.error("Failed to create ContactMessages entry. campaignMessageId={}",
            // event.getCampaignMessageId(), e);
        }
    }

    private void markAsProcessed(String eventId) {
        processedEventIds.put(eventId, System.currentTimeMillis());

        if (processedEventIds.size() > 10000) {
            cleanupOldEventIds();
        }
    }

    
    /**
     * Cleanup old event IDs
     */
    private void cleanupOldEventIds() {
        long cutoff = System.currentTimeMillis() - IDEMPOTENCY_WINDOW_MS;
        processedEventIds.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        // log.debug("Cleaned up old event IDs. Remaining: {}",
        // processedEventIds.size());
    }

}
