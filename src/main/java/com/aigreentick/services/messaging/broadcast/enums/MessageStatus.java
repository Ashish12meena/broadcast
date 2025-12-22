package com.aigreentick.services.messaging.broadcast.enums;

/**
 * Enum representing WhatsApp message statuses.
 * Maps to database ENUM values in lowercase.
 */
public enum MessageStatus {
    PENDING("pending"),
    SENT("sent"),
    DELIVERED("delivered"),
    READ("read"),
    FAILED("failed"),
    ACCEPTED("accepted");

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
        
        String lowerValue = value.toLowerCase();
        
        for (MessageStatus status : MessageStatus.values()) {
            if (status.value.equals(lowerValue)) {
                return status;
            }
        }
        
        // Default fallback
        return PENDING;
    }
}