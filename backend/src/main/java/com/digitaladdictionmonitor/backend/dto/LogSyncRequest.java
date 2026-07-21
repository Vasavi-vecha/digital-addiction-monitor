package com.digitaladdictionmonitor.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** POST /api/logs/sync request body: one user's batch of usage-log rows. */
public class LogSyncRequest {

    @NotBlank
    private String userId;

    @NotEmpty
    @Valid
    private List<UsageLogDto> logs;

    public LogSyncRequest() {
        // Jackson
    }

    public LogSyncRequest(String userId, List<UsageLogDto> logs) {
        this.userId = userId;
        this.logs = logs;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<UsageLogDto> getLogs() {
        return logs;
    }

    public void setLogs(List<UsageLogDto> logs) {
        this.logs = logs;
    }
}
