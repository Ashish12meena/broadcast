package com.aigreentick.services.messaging.broadcast.client.dto;

import java.util.List;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Template {
     private Long id;
    private Long user_id;;
    private String name;
    private String previous_category;
    private String language;
    private String status;
    private String category;
    private String waId;
    private String payload;
    private String response;
    private String template_type;
    private List<TemplateComponent> components;
}
