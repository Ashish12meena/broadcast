package com.aigreentick.services.messaging.broadcast.kafka.event;

import java.util.UUID;

import com.aigreentick.services.messaging.broadcast.dto.build.BuildTemplate;
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
    
    private Long broadcstId; // Campaign this message belongs to
    
    private Long broadcastReportId; // MongoDB CampaignMessages ID
    
    private Long userId;
    
    private String phoneNumberId;
    
    private String accessToken;
    
    private String recipient; // Phone number
    
    private BuildTemplate buildTemplateReqest; // Template message to send
    
    private MessageStatus status; // Current status
    
    private Integer retryCount; // Number of retry attempts
    
    private Long timestamp; // Event creation timestamp
    
    private Integer priority; // Message priority (optional)
    
    /**
     * Creates a new event for a campaign message.
     */
    public static BroadcastReportEvent create(
            Long broadcastId,
            Long broadcastReportId,
            Long userId,
            String phoneNumberId,
            String accessToken,
            String recipient,
            BuildTemplate buildTemplateReqest) {
        
        return BroadcastReportEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .broadcstId(broadcastId)
                .broadcastReportId(broadcastReportId)
                .userId(userId)
                .phoneNumberId(phoneNumberId)
                .accessToken(accessToken)
                .recipient(recipient)
                .buildTemplateReqest(buildTemplateReqest)
                .status(MessageStatus.PENDING)
                .retryCount(0)
                .timestamp(System.currentTimeMillis())
                .priority(0)
                .build();
    }
    
}