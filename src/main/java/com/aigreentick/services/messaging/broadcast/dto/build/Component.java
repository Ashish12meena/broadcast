package com.aigreentick.services.messaging.broadcast.dto.build;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Component {
    private String type;
    
    @JsonProperty("sub_type")
    private String subType;
    
    private String index;
    
    private List<Map<String, Object>> parameters;
}