package com.digitaladdictionmonitor.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One app-usage session row synced from the Android client. Field names and
 * types mirror ml-service's generator.SCHEMA_COLUMNS / UsageLogRow schema so
 * a row can be forwarded to ml-service with no reshaping.
 */
@Entity
@Table(name = "usage_logs", indexes = {
        @Index(name = "idx_usage_logs_user_id", columnList = "userId"),
        @Index(name = "idx_usage_logs_user_id_timestamp", columnList = "userId,timestamp")
})
public class UsageLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String appName;

    @Column(nullable = false)
    private String appCategory;

    @Column(nullable = false)
    private double sessionDurationSec;

    @Column(nullable = false)
    private int unlockCount;

    @Column(nullable = false)
    private boolean isNightUsage;

    protected UsageLogEntity() {
        // JPA
    }

    public UsageLogEntity(
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

    public Long getId() {
        return id;
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
