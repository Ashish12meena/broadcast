package com.aigreentick.services.messaging.broadcast.enums;

public enum BroadcastStatus {

    FAILED(0),
    PENDING(1),
    SUCCESS(2);

    private final int code;

    BroadcastStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BroadcastStatus fromCode(int code) {
        for (BroadcastStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("Unknown BroadcastStatus code: " + code);
    }
}
