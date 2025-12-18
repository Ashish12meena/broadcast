package com.aigreentick.services.messaging.broadcast.enums;

public enum IsMediaStatus {
    NO("0"),
    YES("1");
    
    private final String value;
    
    IsMediaStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}
