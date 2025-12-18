package com.aigreentick.services.messaging.broadcast.client.service;

import com.aigreentick.services.messaging.broadcast.client.dto.Template;

public class TemplateService {

    public Template findByNameAndUserIdNotDeleted(String templatename, Long id) {
        return Template.builder()
        .build();
    }
    
}
