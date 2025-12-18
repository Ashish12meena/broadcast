package com.aigreentick.services.messaging.broadcast.enums;

public enum BroadcastLogStatus {
    success,  // lowercase matches DB
    pending,
    failed;
    
    public static BroadcastLogStatus fromString(String value) {
        return valueOf(value.toLowerCase());
    }
}
