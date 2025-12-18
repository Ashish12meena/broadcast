package com.aigreentick.services.messaging.broadcast.dto.build;

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
    private String messagingProduct = "whatsapp";
    
    @Builder.Default
    private String recipientType = "individual";

    @NotNull
    private String to;

    private SendableTemplate template;

}
