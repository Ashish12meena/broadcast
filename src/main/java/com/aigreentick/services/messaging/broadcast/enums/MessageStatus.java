package com.aigreentick.services.messaging.broadcast.enums;

/**
 * Enum representing WhatsApp message statuses.
 * Maps to database ENUM values.
 */
public enum MessageStatus {
    PENDING("PENDING"),
    SENT("SENT"),
    DELIVERED("DELIVERED"),
    READ("READ"),
    FAILED("FAILED"),
    ACCEPTED("ACCEPTED");

    private final String value;

    MessageStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Convert string value to enum.
     * Handles case-insensitive matching.
     */
    public static MessageStatus fromValue(String value) {
        if (value == null) {
            return PENDING;
        }
        
        String upperValue = value.toUpperCase();
        
        for (MessageStatus status : MessageStatus.values()) {
            if (status.value.equals(upperValue)) {
                return status;
            }
        }
        
        // Default fallback
        return PENDING;
    }
}