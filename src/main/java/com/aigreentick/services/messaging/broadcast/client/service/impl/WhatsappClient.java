package com.aigreentick.services.messaging.broadcast.client.service.impl;

import org.springframework.stereotype.Component;

import com.aigreentick.services.messaging.broadcast.client.dto.FacebookApiResponse;
import com.aigreentick.services.messaging.broadcast.client.service.WhatsappClientService;
import com.aigreentick.services.messaging.broadcast.dto.response.SendTemplateMessageResponse;

import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
@Component
public class WhatsappClient {
    private final WhatsappClientService whatsappClientService;

    public FacebookApiResponse<SendTemplateMessageResponse> sendMessage(String payload, String phoneNumberId,
            String accessToken) {
        
        return whatsappClientService.sendMessage(payload, phoneNumberId, accessToken);
    }

}
