package com.aigreentick.services.messaging.broadcast.enums;

public enum BroadcastLogStatus {
    failed,
    pending,
    success;
    
    // Helper method if you need it
    public static BroadcastLogStatus fromString(String value) {
        if (value == null) {
            return pending;
        }
        try {
            return valueOf(value.toLowerCase());
        } catch (IllegalArgumentException e) {
            return pending;
        }
    }
}