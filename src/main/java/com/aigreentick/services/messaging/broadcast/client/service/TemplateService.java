package com.aigreentick.services.messaging.broadcast.client.service;

import org.springframework.stereotype.Service;

import com.aigreentick.services.messaging.broadcast.client.dto.Template;

@Service
public class TemplateService {

    public Template findByNameAndUserIdNotDeleted(String templatename, Long id) {
        return Template.builder()
        .build();
    }
    
}
