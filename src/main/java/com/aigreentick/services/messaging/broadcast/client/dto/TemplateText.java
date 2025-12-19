package com.aigreentick.services.messaging.broadcast.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateText {
    private Long id;
    private String text;
    private Integer text_index;
}
