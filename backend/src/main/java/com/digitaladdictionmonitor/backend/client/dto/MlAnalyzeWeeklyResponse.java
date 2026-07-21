package com.digitaladdictionmonitor.backend.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Wire shape for ml-service's app.schemas.AnalyzeWeeklyResponse. */
public class MlAnalyzeWeeklyResponse {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("week_index")
    private int weekIndex;

    @JsonProperty("risk_score")
    private double riskScore;

    @JsonProperty("cluster_persona")
    private String clusterPersona;

    @JsonProperty("top_drivers")
    private List<MlBehaviouralDriverDto> topDrivers;

    @JsonProperty("week_over_week_deltas")
    private List<MlWeekOverWeekDeltaDto> weekOverWeekDeltas;

    public MlAnalyzeWeeklyResponse() {
        // Jackson
    }

    public String getUserId() {
        return userId;
    }

    public int getWeekIndex() {
        return weekIndex;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public String getClusterPersona() {
        return clusterPersona;
    }

    public List<MlBehaviouralDriverDto> getTopDrivers() {
        return topDrivers;
    }

    public List<MlWeekOverWeekDeltaDto> getWeekOverWeekDeltas() {
        return weekOverWeekDeltas;
    }
}
