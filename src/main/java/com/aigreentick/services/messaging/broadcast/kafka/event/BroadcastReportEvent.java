package com.aigreentick.services.messaging.broadcast.kafka.event;

import java.util.UUID;

import com.aigreentick.services.messaging.broadcast.enums.MessageStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class BroadcastReportEvent {
    private String eventId; // Unique event identifier
    
    private Long broadcastId; // Campaign this message belongs to
    
    private Long userId;
    
    private String phoneNumberId;
    
    private String accessToken;
    
    private String recipient; // Phone number
    
    private String payload; // Pre-built template payload as JSON string
    
    private MessageStatus status; // Current status
    
    private Integer retryCount; // Number of retry attempts
    
    private Long timestamp; // Event creation timestamp
    
    private Integer priority; // Message priority (optional)
    
    /**
     * Creates event for dispatch flow (pre-built payload)
     */
    public static BroadcastReportEvent createForDispatch(
            Long broadcastId,
            Long userId,
            String phoneNumberId,
            String accessToken,
            String recipient,
            String payload) {
        
        return BroadcastReportEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .broadcastId(broadcastId)
                .userId(userId)
                .phoneNumberId(phoneNumberId)
                .accessToken(accessToken)
                .recipient(recipient)
                .payload(payload)
                .status(MessageStatus.PENDING)
                .retryCount(0)
                .timestamp(System.currentTimeMillis())
                .priority(0)
                .build();
    }
}