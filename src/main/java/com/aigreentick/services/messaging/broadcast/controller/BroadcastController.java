package com.aigreentick.services.messaging.broadcast.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.messaging.broadcast.dto.BroadcastDispatchRequestDto;
import com.aigreentick.services.messaging.broadcast.service.impl.BroadcastOrchestratorServiceImpl;
import com.aigreentick.services.messaging.broadcast.service.impl.BroadcastOrchestratorServiceImpl.ResponseMessage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for broadcast operations.
 * Handles dispatching pre-built WhatsApp templates to Kafka.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/broadcast")
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastOrchestratorServiceImpl broadcastOrchestrator;

    /**
     * Dispatch pre-built templates to Kafka.
     * Reports are already created by another service.
     * This endpoint just publishes to Kafka and returns immediately.
     * 
     * @param request Dispatch request with pre-built payloads
     * @return Response with dispatch status
     */
    @PostMapping("/dispatch")
    public ResponseEntity<ResponseMessage<DispatchResult>> dispatchBroadcast(
            @Valid @RequestBody BroadcastDispatchRequestDto request) {

        log.info("=== Dispatch Request Received ===");
        log.info("Items count: {}", request.getItems() != null ? request.getItems().size() : 0);

        try {
            ResponseMessage<DispatchResult> response = broadcastOrchestrator.handleDispatch(request);

            if ("SUCCESS".equals(response.getStatus())) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Dispatch failed with exception", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResponseMessage.error("Dispatch failed: " + e.getMessage()));
        }
    }

    @GetMapping("/check")
    public ResponseEntity<String> checkRunning() {
        log.info("=== check Running Request Received ===");
        return ResponseEntity.ok("Broadcast service is running");
    }

    /**
     * Health check endpoint to verify broadcast service is running.
     */
    @PostMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Broadcast service is running");
    }

    /**
     * Response for dispatch endpoint
     */
    public record DispatchResult(
            int totalDispatched,
            int failedCount,
            String message) {
    }
}