package com.digitaladdictionmonitor.backend.client.dto;

/** Wire shape for ml-service's app.schemas.BehaviouralDriver. */
public class MlBehaviouralDriverDto {

    private String metric;
    private String description;
    private double zscore;

    public MlBehaviouralDriverDto() {
        // Jackson
    }

    public MlBehaviouralDriverDto(String metric, String description, double zscore) {
        this.metric = metric;
        this.description = description;
        this.zscore = zscore;
    }

    public String getMetric() {
        return metric;
    }

    public String getDescription() {
        return description;
    }

    public double getZscore() {
        return zscore;
    }
}
