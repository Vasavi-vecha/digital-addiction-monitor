package com.digitaladdictionmonitor.backend.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Wire shape for ml-service's app.schemas.UsageLogRow (snake_case JSON). */
public class MlUsageLogRowDto {

    @JsonProperty("user_id")
    private String userId;

    private Instant timestamp;

    @JsonProperty("app_name")
    private String appName;

    @JsonProperty("app_category")
    private String appCategory;

    @JsonProperty("session_duration_sec")
    private double sessionDurationSec;

    @JsonProperty("unlock_count")
    private int unlockCount;

    @JsonProperty("is_night_usage")
    private boolean isNightUsage;

    public MlUsageLogRowDto() {
        // Jackson
    }

    public MlUsageLogRowDto(
            String userId,
            Instant timestamp,
            String appName,
            String appCategory,
            double sessionDurationSec,
            int unlockCount,
            boolean isNightUsage
    ) {
        this.userId = userId;
        this.timestamp = timestamp;
        this.appName = appName;
        this.appCategory = appCategory;
        this.sessionDurationSec = sessionDurationSec;
        this.unlockCount = unlockCount;
        this.isNightUsage = isNightUsage;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getAppName() {
        return appName;
    }

    public String getAppCategory() {
        return appCategory;
    }

    public double getSessionDurationSec() {
        return sessionDurationSec;
    }

    public int getUnlockCount() {
        return unlockCount;
    }

    public boolean isNightUsage() {
        return isNightUsage;
    }
}
