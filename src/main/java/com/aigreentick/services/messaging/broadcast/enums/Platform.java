package com.aigreentick.services.messaging.broadcast.enums;


public enum Platform {
    API("api"),
    WEB("web");

    private final String dbValue;

    Platform(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static Platform fromDbValue(String value) {
        for (Platform p : values()) {
            if (p.dbValue.equalsIgnoreCase(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown platform: " + value);
    }
}
