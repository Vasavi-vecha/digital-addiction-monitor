package com.digitaladdictionmonitor.backend.dto;

/** This-week-vs-personal-baseline delta for one metric, passed through from ml-service. */
public class BaselineDeltaDto {

    private final String metric;
    private final double currentValue;
    private final double previousValue;
    private final double pctChange;

    public BaselineDeltaDto(String metric, double currentValue, double previousValue, double pctChange) {
        this.metric = metric;
        this.currentValue = currentValue;
        this.previousValue = previousValue;
        this.pctChange = pctChange;
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
