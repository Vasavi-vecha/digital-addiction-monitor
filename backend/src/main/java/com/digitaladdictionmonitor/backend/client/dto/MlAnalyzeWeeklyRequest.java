package com.digitaladdictionmonitor.backend.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Wire shape for ml-service's POST /analyze/weekly request. */
public class MlAnalyzeWeeklyRequest {

    @JsonProperty("user_id")
    private final String userId;

    private final List<MlUsageLogRowDto> logs;

    public MlAnalyzeWeeklyRequest(String userId, List<MlUsageLogRowDto> logs) {
        this.userId = userId;
        this.logs = logs;
    }

    public String getUserId() {
        return userId;
    }

    public List<MlUsageLogRowDto> getLogs() {
        return logs;
    }
}
