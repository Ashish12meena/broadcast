package com.aigreentick.services.messaging.broadcast.client.service;

import com.aigreentick.services.messaging.broadcast.client.dto.FacebookApiResponse;
import com.aigreentick.services.messaging.broadcast.dto.response.SendTemplateMessageResponse;

/**
 * Interface for WhatsApp messaging operations.
 * Allows switching between real and mock implementations via Spring profiles.
 */
public interface WhatsappClientService {
    
    /**
     * Sends a WhatsApp message using a pre-approved template.
     * 
     * @param bodyJson JSON payload for the message
     * @param phoneNumberId WhatsApp Business Phone Number ID
     * @param accessToken WhatsApp Business API access token
     * @return Response containing message details or error information
     */
    FacebookApiResponse<SendTemplateMessageResponse> sendMessage(
            String bodyJson, 
            String phoneNumberId, 
            String accessToken);
}