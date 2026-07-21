package com.digitaladdictionmonitor.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/** One usage-session row as uploaded by the Android client in a sync batch. */
public class UsageLogDto {

    @NotNull
    private Instant timestamp;

    @NotBlank
    private String appName;

    @NotBlank
    private String appCategory;

    @PositiveOrZero
    private double sessionDurationSec;

    @Min(0)
    private int unlockCount;

    private boolean isNightUsage;

    public UsageLogDto() {
        // Jackson
    }

    public UsageLogDto(
            Instant timestamp,
            String appName,
            String appCategory,
            double sessionDurationSec,
            int unlockCount,
            boolean isNightUsage
    ) {
        this.timestamp = timestamp;
        this.appName = appName;
        this.appCategory = appCategory;
        this.sessionDurationSec = sessionDurationSec;
        this.unlockCount = unlockCount;
        this.isNightUsage = isNightUsage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppCategory() {
        return appCategory;
    }

    public void setAppCategory(String appCategory) {
        this.appCategory = appCategory;
    }

    public double getSessionDurationSec() {
        return sessionDurationSec;
    }

    public void setSessionDurationSec(double sessionDurationSec) {
        this.sessionDurationSec = sessionDurationSec;
    }

    public int getUnlockCount() {
        return unlockCount;
    }

    public void setUnlockCount(int unlockCount) {
        this.unlockCount = unlockCount;
    }

    public boolean isNightUsage() {
        return isNightUsage;
    }

    public void setNightUsage(boolean nightUsage) {
        isNightUsage = nightUsage;
    }
}
