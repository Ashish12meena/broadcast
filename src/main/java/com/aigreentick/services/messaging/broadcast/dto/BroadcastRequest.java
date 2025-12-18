package com.aigreentick.services.messaging.broadcast.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class BroadcastRequest {
    private String apiKey;
    private Long templateId;
    private String templatename;
    private List<String> mobileNumbers;
    private Long countryId;
    private String campName;
    private Boolean isMedia;
    private String mediaType;
    private String mediaUrl;
    private String scheduleDate;
    private Object variables; // Can be String or List<String>
    private List<Map<String, Object>> carouselCards;
}
