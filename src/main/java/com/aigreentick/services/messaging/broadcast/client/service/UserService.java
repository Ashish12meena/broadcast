package com.aigreentick.services.messaging.broadcast.client.service;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.client.dto.WhatsappAccount;


@Service
public class UserService {

    public WhatsappAccount findActiveByUserId(Long id) {
       return new WhatsappAccount(1L,1L,"730591696796813","EAAOcfziRygMBPGSZCjTEADbcIXleBDVHuZAF61EDXn6qw2GuS6ghjiVHESlosKbAFGEAGMkArSBqyyyaqUxS51dSiLFtZBRd0oEZAY1LiNElHPcM3bsRzqNjaQZAXht6WOKuEWEGfotJASpCGqMOKBrXUMQr03TopqfrZCBe4xrmlfwVipb6dYQaVkmn8gCqzN","active");
    }
    
}
