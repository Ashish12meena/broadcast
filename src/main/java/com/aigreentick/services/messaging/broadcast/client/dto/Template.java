package com.aigreentick.services.messaging.broadcast.client.dto;

import java.util.List;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Template {
    private Long id;
    private String name;
    private String category;
    private String language;
    private String status;
    private String metaTemplateId;
    private List<TemplateComponent> components;

}
