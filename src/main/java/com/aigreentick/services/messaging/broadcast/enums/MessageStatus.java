package com.aigreentick.services.messaging.broadcast.enums;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageStatus {
    //  PENDING("PENDING"),      // Not yet sent to WhatsApp
    // SENT("SENT"),            // Accepted by WhatsApp API
    // DELIVERED("DELIVERED"),  // Delivered to recipient
    // READ("READ"),
    // FAILED("FAILED"),
    // ACCEPTED("ACCEPTED");

    // private final String value;

    // MessageStatus(String value) {
    //     this.value = value;
    // }

    // @JsonValue
    // public String getValue() {
    //     return value;
    // }

    // @JsonCreator
    // public static MessageStatus fromValue(String value) {
    //     for (MessageStatus status : MessageStatus.values()) {
    //         if (status.value.equalsIgnoreCase(value)) {
    //             return status;
    //         }
    //     }
    //     throw new IllegalArgumentException("Unknown MessageStatusEnum: " + value);
    // }
}
