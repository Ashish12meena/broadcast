package com.aigreentick.services.messaging.broadcast.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "whatsapp-service")
@Data
public class WhatsappClientProperties {
    private String baseUrl;
    private String apiVersion;
    
    // Feature flags for dynamic enable/disable
    private volatile boolean outgoingEnabled = true;
    private volatile boolean incomingEnabled = true;
}
