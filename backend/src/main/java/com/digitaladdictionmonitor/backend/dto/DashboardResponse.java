package com.digitaladdictionmonitor.backend.dto;

import java.util.List;
import java.util.Map;

/** GET /api/dashboard/{userId} response: everything the dashboard screen needs in one payload. */
public class DashboardResponse {

    private final String userId;
    private final double riskScore;
    private final String trendDirection;
    private final Map<String, Double> weeklyCategoryTotalsMinutes;
    private final FocusMetricsDto focusMetrics;
    private final List<BaselineDeltaDto> baselineDeltas;
    private final String clusterPersona;
    private final List<SuggestionDto> suggestions;

    public DashboardResponse(
            String userId,
            double riskScore,
            String trendDirection,
            Map<String, Double> weeklyCategoryTotalsMinutes,
            FocusMetricsDto focusMetrics,
            List<BaselineDeltaDto> baselineDeltas,
            String clusterPersona,
            List<SuggestionDto> suggestions
    ) {
        this.userId = userId;
        this.riskScore = riskScore;
        this.trendDirection = trendDirection;
        this.weeklyCategoryTotalsMinutes = weeklyCategoryTotalsMinutes;
        this.focusMetrics = focusMetrics;
        this.baselineDeltas = baselineDeltas;
        this.clusterPersona = clusterPersona;
        this.suggestions = suggestions;
    }

    public String getUserId() {
        return userId;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public String getTrendDirection() {
        return trendDirection;
    }

    public Map<String, Double> getWeeklyCategoryTotalsMinutes() {
        return weeklyCategoryTotalsMinutes;
    }

    public FocusMetricsDto getFocusMetrics() {
        return focusMetrics;
    }

    public List<BaselineDeltaDto> getBaselineDeltas() {
        return baselineDeltas;
    }

    public String getClusterPersona() {
        return clusterPersona;
    }

    public List<SuggestionDto> getSuggestions() {
        return suggestions;
    }
}
