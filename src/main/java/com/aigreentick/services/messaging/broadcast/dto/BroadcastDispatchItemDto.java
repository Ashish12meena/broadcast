package com.aigreentick.services.messaging.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class BroadcastDispatchItemDto {

    @NotNull
    private Long broadcastId;

    @NotBlank
    private String mobileNo;

    @NotBlank
    private String payload;
}

