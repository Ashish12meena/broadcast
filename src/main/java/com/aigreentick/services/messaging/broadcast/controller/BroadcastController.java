package com.aigreentick.services.messaging.broadcast.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.messaging.broadcast.dto.BroadcastRequest;
import com.aigreentick.services.messaging.broadcast.service.impl.BroadcastOrchestratorServiceImpl;
import com.aigreentick.services.messaging.broadcast.service.impl.BroadcastOrchestratorServiceImpl.BroadcastResult;
import com.aigreentick.services.messaging.broadcast.service.impl.BroadcastOrchestratorServiceImpl.ResponseMessage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for broadcast operations.
 * Provides endpoints to initiate and manage WhatsApp broadcasts.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/broadcast")
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastOrchestratorServiceImpl broadcastOrchestrator;

    /**
     * Initiate a new broadcast campaign.
     * 
     * @param request Broadcast request containing template, recipients, etc.
     * @return Response with broadcast ID and status
     */
    @PostMapping
    public ResponseEntity<ResponseMessage<BroadcastResult>> initiateBroadcast(
            @Valid @RequestBody BroadcastRequest request) {
        
        log.info("Received broadcast request. Template: {}, Recipients: {}", 
            request.getTemplatename(), 
            request.getMobileNumbers() != null ? request.getMobileNumbers().size() : 0);
        
        try {
            // Validate request
            if (request.getMobileNumbers() == null || request.getMobileNumbers().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ResponseMessage.error("Mobile numbers list cannot be empty"));
            }
            
            if (request.getTemplatename() == null || request.getTemplatename().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ResponseMessage.error("Template name is required"));
            }
            
            // Process broadcast
            ResponseMessage<BroadcastResult> response = broadcastOrchestrator.handleBroadcast(request);
            
            // Return appropriate HTTP status
            if ("SUCCESS".equals(response.getStatus())) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
        } catch (Exception e) {
            log.error("Failed to process broadcast request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseMessage.error("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Health check endpoint to verify broadcast service is running.
     */
    @PostMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Broadcast service is running");
    }
}