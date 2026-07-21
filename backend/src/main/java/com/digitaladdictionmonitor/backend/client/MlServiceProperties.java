package com.digitaladdictionmonitor.backend.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ml-service")
public record MlServiceProperties(String baseUrl, long timeoutMs) {
}
