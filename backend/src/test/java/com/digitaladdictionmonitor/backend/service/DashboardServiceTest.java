package com.digitaladdictionmonitor.backend.service;

import com.digitaladdictionmonitor.backend.client.MlServiceClient;
import com.digitaladdictionmonitor.backend.client.dto.MlAnalyzeWeeklyResponse;
import com.digitaladdictionmonitor.backend.client.dto.MlBehaviouralDriverDto;
import com.digitaladdictionmonitor.backend.client.dto.MlWeekOverWeekDeltaDto;
import com.digitaladdictionmonitor.backend.dto.DashboardResponse;
import com.digitaladdictionmonitor.backend.entity.UsageLogEntity;
import com.digitaladdictionmonitor.backend.exception.MlServiceUnavailableException;
import com.digitaladdictionmonitor.backend.exception.UserNotFoundException;
import com.digitaladdictionmonitor.backend.repository.UsageLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UsageLogRepository usageLogRepository;

    @Mock
    private MlServiceClient mlServiceClient;

    // Real, not mocked -- it's stateless and its own behaviour is covered
    // exhaustively by SuggestionEngineTest; here we only need to confirm it's
    // wired in correctly.
    private final SuggestionEngine suggestionEngine = new SuggestionEngine();

    private DashboardService service() {
        return new DashboardService(usageLogRepository, mlServiceClient, suggestionEngine);
    }

    private static UsageLogEntity log(String userId, Instant timestamp, String category, double durationSec) {
        return new UsageLogEntity(userId, timestamp, "SomeApp", category, durationSec, 0, false);
    }

    @Test
    void emptyLogsThrowsUserNotFound() {
        when(usageLogRepository.findByUserIdOrderByTimestampAsc("u9999")).thenReturn(List.of());

        assertThatThrownBy(() -> service().getDashboard("u9999"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("u9999");
    }

    @Test
    void mlServiceUnavailableExceptionPropagatesUncaught() {
        List<UsageLogEntity> logs = List.of(log("u0001", Instant.parse("2024-01-01T10:00:00Z"), "social", 60));
        when(usageLogRepository.findByUserIdOrderByTimestampAsc("u0001")).thenReturn(logs);
        when(mlServiceClient.analyzeWeekly(eq("u0001"), anyList()))
                .thenThrow(new MlServiceUnavailableException("boom", new RuntimeException()));

        assertThatThrownBy(() -> service().getDashboard("u0001"))
                .isInstanceOf(MlServiceUnavailableException.class);
    }

    @Test
    void categoryTotalsOnlyIncludeMostRecentMondayToSundayWindow() {
        // 2024-01-01 is a Monday. latestDate = 2024-01-05 (Friday, same week)
        // -> window is [2024-01-01, 2024-01-08).
        List<UsageLogEntity> logs = List.of(
                log("u0001", Instant.parse("2024-01-01T10:00:00Z"), "social", 600),         // in window
                log("u0001", Instant.parse("2024-01-05T10:00:00Z"), "productivity", 1200),  // in window (latest)
                log("u0001", Instant.parse("2023-12-25T10:00:00Z"), "social", 99999)         // prior week, excluded
        );
        when(usageLogRepository.findByUserIdOrderByTimestampAsc("u0001")).thenReturn(logs);
        when(mlServiceClient.analyzeWeekly(eq("u0001"), anyList())).thenReturn(
                new MlAnalyzeWeeklyResponse("u0001", 5, 30.0, "healthy", List.of(), List.of())
        );

        DashboardResponse response = service().getDashboard("u0001");

        assertThat(response.getWeeklyCategoryTotalsMinutes())
                .containsEntry("social", 10.0)
                .containsEntry("productivity", 20.0)
                .hasSize(2);
    }

    @Test
    void extractFocusMetricsDefaultsToZeroWhenMetricMissingFromDeltas() {
        List<UsageLogEntity> logs = List.of(log("u0001", Instant.parse("2024-01-01T10:00:00Z"), "social", 60));
        when(usageLogRepository.findByUserIdOrderByTimestampAsc("u0001")).thenReturn(logs);
        // deltas present but neither context_switch_rate nor deep_focus_minutes included
        List<MlWeekOverWeekDeltaDto> deltas = List.of(
                new MlWeekOverWeekDeltaDto("total_screen_min", 300.0, 280.0, 7.0)
        );
        when(mlServiceClient.analyzeWeekly(eq("u0001"), anyList())).thenReturn(
                new MlAnalyzeWeeklyResponse("u0001", 5, 30.0, "healthy", List.of(), deltas)
        );

        DashboardResponse response = service().getDashboard("u0001");

        assertThat(response.getFocusMetrics().getContextSwitchRate()).isEqualTo(0.0);
        assertThat(response.getFocusMetrics().getDeepFocusMinutes()).isEqualTo(0.0);
    }

    @Test
    void happyPathAssemblesFullDashboardResponse() {
        List<UsageLogEntity> logs = List.of(
                log("u0001", Instant.parse("2024-01-01T10:00:00Z"), "social", 600),
                log("u0001", Instant.parse("2024-01-05T10:00:00Z"), "productivity", 1200)
        );
        when(usageLogRepository.findByUserIdOrderByTimestampAsc("u0001")).thenReturn(logs);

        List<MlWeekOverWeekDeltaDto> deltas = List.of(
                new MlWeekOverWeekDeltaDto("total_screen_min", 330.0, 300.0, 10.0),
                new MlWeekOverWeekDeltaDto("night_ratio", 0.2, 0.19, 5.0),
                new MlWeekOverWeekDeltaDto("context_switch_rate", 5.2, 5.0, -5.0),
                new MlWeekOverWeekDeltaDto("unlock_count_week", 100.0, 98.0, 2.0),
                new MlWeekOverWeekDeltaDto("deep_focus_minutes", 80.0, 90.0, 1.0)
        );
        List<MlBehaviouralDriverDto> drivers = List.of(
                new MlBehaviouralDriverDto("total_screen_min", "desc", 2.5),
                new MlBehaviouralDriverDto("night_ratio", "desc", -1.0)
        );
        when(mlServiceClient.analyzeWeekly(eq("u0001"), anyList())).thenReturn(
                new MlAnalyzeWeeklyResponse("u0001", 5, 42.5, "borderline", drivers, deltas)
        );

        DashboardResponse response = service().getDashboard("u0001");

        assertThat(response.getUserId()).isEqualTo("u0001");
        assertThat(response.getRiskScore()).isEqualTo(42.5);
        assertThat(response.getClusterPersona()).isEqualTo("borderline");
        assertThat(response.getTrendDirection()).isEqualTo("increasing"); // 3 worsening votes, see below
        assertThat(response.getFocusMetrics().getContextSwitchRate()).isEqualTo(5.2);
        assertThat(response.getFocusMetrics().getDeepFocusMinutes()).isEqualTo(80.0);
        assertThat(response.getBaselineDeltas()).hasSize(5);
        assertThat(response.getSuggestions()).hasSize(2);
        assertThat(response.getSuggestions().get(0).getMetric()).isEqualTo("total_screen_min"); // |2.5| > |-1.0|
    }

    @ParameterizedTest(name = "{0} worsening votes -> {1}")
    @CsvSource({
            "0, decreasing",
            "1, decreasing",
            "2, stable",
            "3, increasing",
            "4, increasing",
            "5, increasing",
    })
    void deriveTrendVoteBoundaries(int worseningVotes, String expectedTrend) {
        List<UsageLogEntity> logs = List.of(log("u0001", Instant.parse("2024-01-01T10:00:00Z"), "social", 60));
        when(usageLogRepository.findByUserIdOrderByTimestampAsc("u0001")).thenReturn(logs);

        // 5 metrics tracked by TREND_METRICS_WORSEN_IF_UP; each worsening
        // vote is a metric whose pctChange sign matches its worsen-if-up flag.
        // The first 4 always worsen-if-up=true, so a positive pctChange casts
        // a vote for each; deep_focus_minutes worsens on DOWN so it never
        // votes here (kept flat/negative), giving exactly `worseningVotes` votes.
        List<MlWeekOverWeekDeltaDto> deltas = List.of(
                new MlWeekOverWeekDeltaDto("total_screen_min", 1, 1, worseningVotes >= 1 ? 5.0 : -5.0),
                new MlWeekOverWeekDeltaDto("night_ratio", 1, 1, worseningVotes >= 2 ? 5.0 : -5.0),
                new MlWeekOverWeekDeltaDto("context_switch_rate", 1, 1, worseningVotes >= 3 ? 5.0 : -5.0),
                new MlWeekOverWeekDeltaDto("unlock_count_week", 1, 1, worseningVotes >= 4 ? 5.0 : -5.0),
                new MlWeekOverWeekDeltaDto("deep_focus_minutes", 1, 1, worseningVotes >= 5 ? -5.0 : 5.0)
        );
        when(mlServiceClient.analyzeWeekly(eq("u0001"), anyList())).thenReturn(
                new MlAnalyzeWeeklyResponse("u0001", 5, 10.0, "healthy", List.of(), deltas)
        );

        DashboardResponse response = service().getDashboard("u0001");

        assertThat(response.getTrendDirection()).isEqualTo(expectedTrend);
    }
}
