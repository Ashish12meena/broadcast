package com.aigreentick.services.messaging.broadcast.client.service.impl;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.client.dto.FacebookApiResponse;
import com.aigreentick.services.messaging.broadcast.client.service.WhatsappClientService;
import com.aigreentick.services.messaging.broadcast.dto.response.SendTemplateMessageResponse;
import com.aigreentick.services.messaging.broadcast.dto.response.SendTemplateMessageResponse.WhatsAppContactDto;
import com.aigreentick.services.messaging.broadcast.dto.response.SendTemplateMessageResponse.WhatsAppMessageDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Mock implementation for stress testing.
 * Simulates Facebook WhatsApp Business API responses without making actual HTTP calls.
 * Active when profile is 'stress-test' or 'mock'.
 */
@Slf4j
@Service
@Profile("stress-test | mock")
public class WhatsappClientMockImpl implements WhatsappClientService {

    private static final Random random = new Random();
    private static final AtomicLong messageCounter = new AtomicLong(0);
    
    // Configurable parameters for realistic simulation
    private static final int MIN_DELAY_MS = 50;
    private static final int MAX_DELAY_MS = 200;
    private static final double FAILURE_RATE = 0.05; // 5% failure rate
    private static final double ACCEPTANCE_RATE = 0.95; // 95% accepted
    
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);

    @Override
    public FacebookApiResponse<SendTemplateMessageResponse> sendMessage(
            String bodyJson, 
            String phoneNumberId, 
            String accessToken) {
        
        long callNumber = totalCalls.incrementAndGet();
        long startTime = System.currentTimeMillis();
        
        try {
            // Simulate realistic network delay
            simulateNetworkDelay();
            
            // Randomly determine if this call should fail
            boolean shouldFail = random.nextDouble() < FAILURE_RATE;
            
            if (shouldFail) {
                return handleMockFailure(callNumber, startTime, phoneNumberId);
            } else {
                return handleMockSuccess(callNumber, startTime, phoneNumberId);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Mock call interrupted. CallNumber={}", callNumber, e);
            failedCalls.incrementAndGet();
            return FacebookApiResponse.error("Request interrupted", 500);
        }
    }

    /**
     * Simulates network delay with random variation.
     */
    private void simulateNetworkDelay() throws InterruptedException {
        int delay = MIN_DELAY_MS + random.nextInt(MAX_DELAY_MS - MIN_DELAY_MS + 1);
        Thread.sleep(delay);
    }

    /**
     * Handles mock successful response.
     */
    private FacebookApiResponse<SendTemplateMessageResponse> handleMockSuccess(
            long callNumber, 
            long startTime, 
            String phoneNumberId) {
        
        successCalls.incrementAndGet();
        
        // Generate realistic message ID
        String messageId = generateMessageId();
        
        // Determine message status (most are accepted)
        String status = random.nextDouble() < ACCEPTANCE_RATE ? "accepted" : "sent";
        
        // Build response
        SendTemplateMessageResponse response = new SendTemplateMessageResponse();
        response.setMessagingProduct("whatsapp");
        
        // Add contact info
        WhatsAppContactDto contact = new WhatsAppContactDto();
        contact.setInput(   phoneNumberId);
        contact.setWaId(phoneNumberId);
        response.setContacts(List.of(contact));
        
        // Add message info
        WhatsAppMessageDto message = new WhatsAppMessageDto(messageId, status);
        response.setMessages(List.of(message));
        
        long duration = System.currentTimeMillis() - startTime;
        
        return FacebookApiResponse.success(response, 200);
    }

    /**
     * Handles mock failure response.
     */
    private FacebookApiResponse<SendTemplateMessageResponse> handleMockFailure(
            long callNumber, 
            long startTime, 
            String phoneNumberId) {
        
        failedCalls.incrementAndGet();
        
        // Randomly select failure type
        String[] errorTypes = {
            "Rate limit exceeded",
            "Invalid phone number",
            "Template not found",
            "Network timeout"
        };
        
        int[] errorCodes = { 429, 400, 404, 503 };
        int errorIndex = random.nextInt(errorTypes.length);
        
        String errorMessage = errorTypes[errorIndex];
        int errorCode = errorCodes[errorIndex];
        
        long duration = System.currentTimeMillis() - startTime;
        
        return FacebookApiResponse.error(errorMessage, errorCode);
    }

    /**
     * Generates realistic WhatsApp message ID.
     * Format: wamid.{UUID}
     */
    private String generateMessageId() {
        long sequenceNum = messageCounter.incrementAndGet();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("wamid.%s_%d", uuid, sequenceNum);
    }

    /**
     * Returns current statistics for monitoring.
     */
    public MockStatistics getStatistics() {
        return new MockStatistics(
                totalCalls.get(),
                successCalls.get(),
                failedCalls.get(),
                calculateSuccessRate()
        );
    }

    /**
     * Calculates success rate percentage.
     */
    private double calculateSuccessRate() {
        long total = totalCalls.get();
        if (total == 0) return 0.0;
        return (successCalls.get() * 100.0) / total;
    }

    /**
     * Resets statistics counters.
     */
    public void resetStatistics() {
        totalCalls.set(0);
        successCalls.set(0);
        failedCalls.set(0);
        log.info("Mock statistics reset");
    }

    /**
     * Statistics holder for mock client.
     */
    public record MockStatistics(
            long totalCalls,
            long successCalls,
            long failedCalls,
            double successRate
    ) {
        @Override
        public String toString() {
            return String.format("Total: %d, Success: %d, Failed: %d, Success Rate: %.2f%%",
                    totalCalls, successCalls, failedCalls, successRate);
        }
    }
}