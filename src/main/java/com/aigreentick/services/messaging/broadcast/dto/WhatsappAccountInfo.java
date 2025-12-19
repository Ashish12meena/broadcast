package com.aigreentick.services.messaging.broadcast.dto;

import lombok.Data;

@Data
public class WhatsappAccountInfo {
    private String phoneNumberId;

    private String accessToken;
}
