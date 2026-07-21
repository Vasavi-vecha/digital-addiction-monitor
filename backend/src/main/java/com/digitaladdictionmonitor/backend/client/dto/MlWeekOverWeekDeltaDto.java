package com.digitaladdictionmonitor.backend.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Wire shape for ml-service's app.schemas.WeekOverWeekDelta. */
public class MlWeekOverWeekDeltaDto {

    private String metric;

    @JsonProperty("current_value")
    private double currentValue;

    @JsonProperty("previous_value")
    private double previousValue;

    @JsonProperty("pct_change")
    private double pctChange;

    public MlWeekOverWeekDeltaDto() {
        // Jackson
    }

    public String getMetric() {
        return metric;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public double getPreviousValue() {
        return previousValue;
    }

    public double getPctChange() {
        return pctChange;
    }
}
