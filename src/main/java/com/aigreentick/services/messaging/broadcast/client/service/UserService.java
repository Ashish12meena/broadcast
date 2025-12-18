package com.aigreentick.services.messaging.broadcast.client.service;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.client.dto.WhatsappAccount;


@Service
public class UserService {

    public WhatsappAccount findActiveByUserId(Long id) {
       return new WhatsappAccount(1L,1L,"whatsappNoId","permanentToken","status");
    }
    
}
