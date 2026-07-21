package com.digitaladdictionmonitor.backend.integration;

import com.digitaladdictionmonitor.backend.client.MlServiceClient;
import com.digitaladdictionmonitor.backend.client.dto.MlAnalyzeWeeklyResponse;
import com.digitaladdictionmonitor.backend.client.dto.MlBehaviouralDriverDto;
import com.digitaladdictionmonitor.backend.client.dto.MlWeekOverWeekDeltaDto;
import com.digitaladdictionmonitor.backend.dto.DashboardResponse;
import com.digitaladdictionmonitor.backend.dto.LogSyncResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves the repository -> service -> controller -> exception-handler chain
 * actually wires together with a real Spring context and a real in-memory
 * H2 DB (see src/test/resources/application.properties), rather than only
 * being verified piecewise by the unit/slice tests. MlServiceClient is the
 * one collaborator swapped out -- no real ml-service process is needed for
 * this backend's own wiring to be proven correct.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DamIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private MlServiceClient mlServiceClient;

    @Test
    void syncThenDashboardRoundTripsThroughRealPersistenceAndService() {
        when(mlServiceClient.analyzeWeekly(org.mockito.ArgumentMatchers.eq("u-integration-1"), any())).thenReturn(
                new MlAnalyzeWeeklyResponse(
                        "u-integration-1", 3, 55.0, "borderline",
                        List.of(new MlBehaviouralDriverDto("night_ratio", "elevated", 2.1)),
                        List.of(new MlWeekOverWeekDeltaDto("night_ratio", 0.3, 0.2, 50.0))
                )
        );

        String syncBody = """
                {
                  "userId": "u-integration-1",
                  "logs": [
                    {
                      "timestamp": "%s",
                      "appName": "Instagram",
                      "appCategory": "social",
                      "sessionDurationSec": 600.0,
                      "unlockCount": 4,
                      "isNightUsage": true
                    }
                  ]
                }
                """.formatted(Instant.parse("2026-07-20T23:30:00Z"));

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<LogSyncResponse> syncResponse = restTemplate.postForEntity(
                "/api/logs/sync", new org.springframework.http.HttpEntity<>(syncBody, headers), LogSyncResponse.class);

        assertThat(syncResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(syncResponse.getBody()).isNotNull();
        assertThat(syncResponse.getBody().getAcceptedCount()).isEqualTo(1);

        ResponseEntity<DashboardResponse> dashboardResponse = restTemplate.getForEntity(
                "/api/dashboard/u-integration-1", DashboardResponse.class);

        assertThat(dashboardResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardResponse body = dashboardResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getUserId()).isEqualTo("u-integration-1");
        assertThat(body.getRiskScore()).isEqualTo(55.0);
        assertThat(body.getClusterPersona()).isEqualTo("borderline");
        assertThat(body.getWeeklyCategoryTotalsMinutes()).containsEntry("social", 10.0);
        assertThat(body.getSuggestions()).hasSize(1);
    }

    @Test
    void dashboardForUnknownUserReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/dashboard/never-synced-user", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
