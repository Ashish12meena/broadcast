package com.aigreentick.services.messaging.broadcast.client.service;

import com.aigreentick.services.messaging.broadcast.client.dto.WhatsappAccount;

public class UserService {

    public WhatsappAccount findActiveByUserId(Long id) {
       return new WhatsappAccount(1L,1L,"whatsappNoId","permanentToken","status");
    }
    
}
