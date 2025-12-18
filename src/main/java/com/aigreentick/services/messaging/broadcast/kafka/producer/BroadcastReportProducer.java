package com.aigreentick.services.messaging.broadcast.kafka.producer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.aigreentick.services.messaging.broadcast.kafka.event.BroadcastReportEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastReportProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.campaign-messages.name}")
    private String topicName;

    /**
     * Publishes a single campaign message event.
     * SendResult
     * @param event The message event to publish
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, Object>> publishMessage(BroadcastReportEvent event) {
        String partitionKey = String.valueOf(event.getBroadcstId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                topicName,
                partitionKey,
                event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish campaign message event. campaignId={} eventId={} recipient={}",
                        event.getBroadcstId(), event.getEventId(), event.getRecipient(), ex);
            } else {
                log.debug("Campaign message event published. campaignId={} eventId={} partition={} offset={}",
                        event.getBroadcstId(),
                        event.getEventId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }

    /**
     * Publishes multiple campaign message events in batch.
     * 
     * @param events List of events to publish
     * @return CompletableFuture that completes when all messages are sent
     */

    public CompletableFuture<Void> publishBatch(List<BroadcastReportEvent> events) {
        long startTime = System.currentTimeMillis();

        log.info("Publishing batch of {} campaign message events", events.size());

        CompletableFuture<?>[] futures = events.stream()
                .map(this::publishMessage)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .whenComplete((result, ex) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (ex != null) {
                        log.error("Batch publish failed after {}ms. Events={}", duration, events.size(), ex);
                    } else {
                        log.info("Batch published successfully in {}ms. Events={}", duration, events.size());
                    }
                });
    }

    /**
     * Publishes messages with explicit timeout.
     * Useful for synchronous flows where we need confirmation.
     */
    public boolean publishMessageSync(BroadcastReportEvent event, long timeoutSeconds) {
        try {
            CompletableFuture<SendResult<String, Object>> future = publishMessage(event);
            future.get(timeoutSeconds, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish message synchronously. campaignId={} eventId={}",
                    event.getBroadcstId(), event.getEventId(), e);
            return false;
        }
    }

    /**
     * Publishes a retry event for failed message.
     */
    public CompletableFuture<SendResult<String, Object>> publishRetry(BroadcastReportEvent event) {
        event.setRetryCount(event.getRetryCount() + 1);
        event.setTimestamp(System.currentTimeMillis());

        log.info("Publishing retry event. campaignId={} eventId={} retryCount={}",
                event.getBroadcstId(), event.getEventId(), event.getRetryCount());

        return publishMessage(event);
    }

}
