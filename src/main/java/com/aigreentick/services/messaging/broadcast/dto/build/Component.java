package com.aigreentick.services.messaging.broadcast.dto.build;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class Component {
    private String type;
    private String subType;
    private String index;
    List<Map<String,Object>> parameters;
}
