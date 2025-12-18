package com.aigreentick.services.messaging.broadcast.enums;

public enum CampaignStatus {

    FAILED(0),
    PENDING(1),
    SUCCESS(2);

    private final int code;

    CampaignStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static CampaignStatus fromCode(int code) {
        for (CampaignStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("Unknown BroadcastStatus code: " + code);
    }
}
