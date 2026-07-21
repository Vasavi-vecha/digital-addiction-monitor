package com.digitaladdictionmonitor.backend.dto;

/**
 * One concrete, supportive suggestion, ranked by expectedImpact (the absolute
 * personal-baseline z-score of the driver it was generated from -- see
 * SuggestionEngine). Higher expectedImpact = further from this user's own
 * normal pattern = ranked first.
 */
public class SuggestionDto {

    private final String metric;
    private final String message;
    private final double expectedImpact;

    public SuggestionDto(String metric, String message, double expectedImpact) {
        this.metric = metric;
        this.message = message;
        this.expectedImpact = expectedImpact;
    }

    public String getMetric() {
        return metric;
    }

    public String getMessage() {
        return message;
    }

    public double getExpectedImpact() {
        return expectedImpact;
    }
}
