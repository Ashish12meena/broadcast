package com.aigreentick.services.messaging.broadcast.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateComponentButton {
    private Long id;
    private Long template_id;
    private Long component_id;
    private String type;
    private String text;
    private String url;
    private String number;
}
