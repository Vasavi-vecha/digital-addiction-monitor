package com.digitaladdictionmonitor.backend.dto;

/** Current-week values for the two focus-related metrics, as computed by ml-service. */
public class FocusMetricsDto {

    private final double contextSwitchRate;
    private final double deepFocusMinutes;

    public FocusMetricsDto(double contextSwitchRate, double deepFocusMinutes) {
        this.contextSwitchRate = contextSwitchRate;
        this.deepFocusMinutes = deepFocusMinutes;
    }

    public double getContextSwitchRate() {
        return contextSwitchRate;
    }

    public double getDeepFocusMinutes() {
        return deepFocusMinutes;
    }
}
