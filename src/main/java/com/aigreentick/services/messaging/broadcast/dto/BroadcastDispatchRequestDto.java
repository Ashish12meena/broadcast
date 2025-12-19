package com.aigreentick.services.messaging.broadcast.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BroadcastDispatchRequestDto {
    
    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<BroadcastDispatchItemDto> items;

    @NotNull(message = "Account info is required")
    @Valid
    private WhatsappAccountInfo accountInfo;
}