package com.aigreentick.services.messaging.broadcast.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public class BroadcastDispatchRequestDto {
    @NotEmpty
    @Valid
    private List<BroadcastDispatchItemDto> items;

    WhatsappAccountInfo accountInfo;
}
