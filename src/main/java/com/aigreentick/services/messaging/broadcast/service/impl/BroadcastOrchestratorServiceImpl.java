package com.aigreentick.services.messaging.broadcast.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.controller.BroadcastController.DispatchResult;
import com.aigreentick.services.messaging.broadcast.dto.BroadcastDispatchItemDto;
import com.aigreentick.services.messaging.broadcast.dto.BroadcastDispatchRequestDto;
import com.aigreentick.services.messaging.broadcast.kafka.event.BroadcastReportEvent;
import com.aigreentick.services.messaging.broadcast.kafka.producer.BroadcastReportProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastOrchestratorServiceImpl {
    private final BroadcastReportProducer broadcastReportProducer;

    /**
     * Handle dispatch flow - publish pre-built templates to Kafka.
     * No report creation, no template building - just publish to Kafka.
     * 
     * Flow:
     * 1. Validate items
     * 2. Create Kafka events from pre-built payloads
     * 3. Publish to Kafka asynchronously
     * 4. Return immediately (don't wait for Kafka or WhatsApp)
     */
    public ResponseMessage<DispatchResult> handleDispatch(BroadcastDispatchRequestDto request) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("=== Starting Dispatch ===");
            log.info("Items: {} | PhoneNumberId: {}", 
                request.getItems().size(),
                request.getAccountInfo().getPhoneNumberId());

            // 1. Validate items
            if (request.getItems() == null || request.getItems().isEmpty()) {
                return ResponseMessage.error("Items list is empty");
            }


            // 3. Create Kafka events from items
            List<BroadcastReportEvent> events = new ArrayList<>();
            int failedCount = 0;

            for (BroadcastDispatchItemDto item : request.getItems()) {
                try {
                    // Validate item
                    if (item.getBroadcastId() == null) {
                        log.warn("Skipping item with null broadcastId");
                        failedCount++;
                        continue;
                    }
                    
                    if (item.getMobileNo() == null || item.getMobileNo().trim().isEmpty()) {
                        log.warn("Skipping item with empty mobile number. broadcastId={}", 
                            item.getBroadcastId());
                        failedCount++;
                        continue;
                    }
                    
                    if (item.getPayload() == null || item.getPayload().trim().isEmpty()) {
                        log.warn("Skipping item with empty payload. broadcastId={} mobile={}", 
                            item.getBroadcastId(), item.getMobileNo());
                        failedCount++;
                        continue;
                    }

                    // Create event
                    BroadcastReportEvent event = BroadcastReportEvent.createForDispatch(
                        item.getBroadcastId(),
                        request.getAccountInfo().getPhoneNumberId(),
                        request.getAccountInfo().getAccessToken(),
                        item.getMobileNo(),
                        item.getPayload()
                    );

                    events.add(event);

                } catch (Exception e) {
                    log.error("Failed to create event for item. broadcastId={} mobile={}", 
                        item.getBroadcastId(), item.getMobileNo(), e);
                    failedCount++;
                }
            }

            if (events.isEmpty()) {
                return ResponseMessage.error("No valid items to dispatch");
            }

            log.info("Created {} Kafka events (Failed: {})", events.size(), failedCount);

            // 4. Publish to Kafka (async - don't wait)
            CompletableFuture<Void> publishFuture = broadcastReportProducer.publishBatch(events);

            // Log completion asynchronously
            publishFuture.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish dispatch events to Kafka. Events={}", 
                        events.size(), ex);
                } else {
                    log.info("Successfully published {} dispatch events to Kafka", events.size());
                }
            });

            // 5. Return immediately (don't wait for Kafka)
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("=== Dispatch Completed Successfully ===");
            log.info("Duration: {}ms | Dispatched: {} | Failed: {}", 
                duration, events.size(), failedCount);

            DispatchResult result = new DispatchResult(
                events.size(),
                failedCount,
                "Dispatch initiated successfully. Processing in background."
            );

            return ResponseMessage.success(
                "Dispatched " + events.size() + " messages to Kafka",
                result
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Dispatch failed after {}ms. Error: {}", duration, e.getMessage(), e);
            return ResponseMessage.error("Dispatch failed: " + e.getMessage());
        }
    }

    /**
     * Response wrapper for API endpoints
     */
    public static class ResponseMessage<T> {
        private final String status;
        private final String message;
        private final T data;

        private ResponseMessage(String status, String message, T data) {
            this.status = status;
            this.message = message;
            this.data = data;
        }

        public static <T> ResponseMessage<T> success(String message, T data) {
            return new ResponseMessage<>("SUCCESS", message, data);
        }

        public static <T> ResponseMessage<T> error(String message) {
            return new ResponseMessage<>("ERROR", message, null);
        }

        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public T getData() { return data; }
    }
}