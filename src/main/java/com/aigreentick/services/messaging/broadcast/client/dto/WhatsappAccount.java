package com.aigreentick.services.messaging.broadcast.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WhatsappAccount {
    private Long id;
    
    private Long userId;
    
    private String whatsappNoId;
    
    private String parmenentToken;
    
    private String status;
}