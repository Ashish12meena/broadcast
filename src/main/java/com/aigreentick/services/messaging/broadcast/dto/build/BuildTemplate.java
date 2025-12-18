package com.aigreentick.services.messaging.broadcast.dto.build;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildTemplate {
    
    @Builder.Default
    @JsonProperty("messaging_product")
    private String messagingProduct = "whatsapp";
    
    @Builder.Default
    @JsonProperty("recipient_type")
    private String recipientType = "individual";

    @NotNull
    private String to;
    
    private String type;

    private SendableTemplate template;
}