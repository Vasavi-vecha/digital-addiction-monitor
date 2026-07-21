package com.digitaladdictionmonitor.backend.dto;

public class LogSyncResponse {

    private final String userId;
    private final int acceptedCount;
    private final String message;

    public LogSyncResponse(String userId, int acceptedCount, String message) {
        this.userId = userId;
        this.acceptedCount = acceptedCount;
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public String getMessage() {
        return message;
    }
}
