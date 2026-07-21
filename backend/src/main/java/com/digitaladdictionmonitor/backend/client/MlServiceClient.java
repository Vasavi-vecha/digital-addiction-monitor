package com.digitaladdictionmonitor.backend.client;

import com.digitaladdictionmonitor.backend.client.dto.MlAnalyzeWeeklyRequest;
import com.digitaladdictionmonitor.backend.client.dto.MlAnalyzeWeeklyResponse;
import com.digitaladdictionmonitor.backend.client.dto.MlUsageLogRowDto;
import com.digitaladdictionmonitor.backend.entity.UsageLogEntity;
import com.digitaladdictionmonitor.backend.exception.MlServiceUnavailableException;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Thin wrapper around ml-service's HTTP API. The backend never reimplements
 * risk-scoring/clustering logic itself -- every /analyze/weekly call here
 * forwards the user's raw persisted logs and trusts ml-service's computed
 * risk score, persona, drivers and deltas, keeping that logic in exactly one
 * place (see ml-service/scoring.py's own train/serve-drift rationale).
 */
@Component
public class MlServiceClient {

    private final RestClient restClient;

    public MlServiceClient(RestClient.Builder restClientBuilder, MlServiceProperties properties) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        var requestFactory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(timeout)
                        .withReadTimeout(timeout)
        );
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public MlAnalyzeWeeklyResponse analyzeWeekly(String userId, List<UsageLogEntity> logs) {
        List<MlUsageLogRowDto> rows = logs.stream()
                .map(log -> new MlUsageLogRowDto(
                        log.getUserId(),
                        log.getTimestamp(),
                        log.getAppName(),
                        log.getAppCategory(),
                        log.getSessionDurationSec(),
                        log.getUnlockCount(),
                        log.isNightUsage()
                ))
                .toList();

        MlAnalyzeWeeklyRequest request = new MlAnalyzeWeeklyRequest(userId, rows);

        try {
            return restClient.post()
                    .uri("/analyze/weekly")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MlAnalyzeWeeklyResponse.class);
        } catch (RestClientException e) {
            throw new MlServiceUnavailableException(
                    "ml-service /analyze/weekly call failed for userId=" + userId, e
            );
        }
    }
}
