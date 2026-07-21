package com.digitaladdictionmonitor.backend.service;

import com.digitaladdictionmonitor.backend.client.MlServiceClient;
import com.digitaladdictionmonitor.backend.client.dto.MlAnalyzeWeeklyResponse;
import com.digitaladdictionmonitor.backend.client.dto.MlWeekOverWeekDeltaDto;
import com.digitaladdictionmonitor.backend.dto.BaselineDeltaDto;
import com.digitaladdictionmonitor.backend.dto.DashboardResponse;
import com.digitaladdictionmonitor.backend.dto.FocusMetricsDto;
import com.digitaladdictionmonitor.backend.dto.SuggestionDto;
import com.digitaladdictionmonitor.backend.entity.UsageLogEntity;
import com.digitaladdictionmonitor.backend.exception.UserNotFoundException;
import com.digitaladdictionmonitor.backend.repository.UsageLogRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    /**
     * Same 5-of-N sign-count heuristic app/risk_service.py's _trend_direction
     * uses, applied here to week_over_week_deltas ml-service already
     * computed -- a small presentation derivation over already-computed
     * numbers, not a reimplementation of the scoring/forecasting model
     * itself (which stays server-side in ml-service, per the "call
     * ml-service, don't reimplement" decision for this backend).
     */
    private static final Map<String, Boolean> TREND_METRICS_WORSEN_IF_UP = Map.of(
            "total_screen_min", true,
            "night_ratio", true,
            "context_switch_rate", true,
            "unlock_count_week", true,
            "deep_focus_minutes", false
    );

    private final UsageLogRepository usageLogRepository;
    private final MlServiceClient mlServiceClient;
    private final SuggestionEngine suggestionEngine;

    public DashboardService(
            UsageLogRepository usageLogRepository,
            MlServiceClient mlServiceClient,
            SuggestionEngine suggestionEngine
    ) {
        this.usageLogRepository = usageLogRepository;
        this.mlServiceClient = mlServiceClient;
        this.suggestionEngine = suggestionEngine;
    }

    public DashboardResponse getDashboard(String userId) {
        List<UsageLogEntity> logs = usageLogRepository.findByUserIdOrderByTimestampAsc(userId);
        if (logs.isEmpty()) {
            throw new UserNotFoundException(userId);
        }

        MlAnalyzeWeeklyResponse analysis = mlServiceClient.analyzeWeekly(userId, logs);

        Map<String, Double> categoryTotals = currentWeekCategoryTotalsMinutes(logs);
        FocusMetricsDto focusMetrics = extractFocusMetrics(analysis.getWeekOverWeekDeltas());
        List<BaselineDeltaDto> baselineDeltas = mapDeltas(analysis.getWeekOverWeekDeltas());
        String trend = deriveTrend(analysis.getWeekOverWeekDeltas());
        List<SuggestionDto> suggestions = suggestionEngine.generate(analysis.getTopDrivers(), analysis.getWeekOverWeekDeltas());

        return new DashboardResponse(
                userId,
                analysis.getRiskScore(),
                trend,
                categoryTotals,
                focusMetrics,
                baselineDeltas,
                analysis.getClusterPersona(),
                suggestions
        );
    }

    /**
     * ml-service's /analyze/weekly response doesn't expose a per-category
     * screen-time breakdown (only risk score, persona, drivers, deltas), so
     * this is the one aggregation the backend does itself, straight off the
     * raw persisted rows for whichever calendar week (Monday-Sunday) holds
     * this user's most recent log.
     */
    private Map<String, Double> currentWeekCategoryTotalsMinutes(List<UsageLogEntity> logs) {
        LocalDate latestDate = logs.stream()
                .map(l -> l.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate())
                .max(LocalDate::compareTo)
                .orElseThrow();
        LocalDate weekStart = latestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEndExclusive = weekStart.plusDays(7);

        Map<String, Double> totals = new LinkedHashMap<>();
        for (UsageLogEntity log : logs) {
            LocalDate logDate = log.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
            if (!logDate.isBefore(weekStart) && logDate.isBefore(weekEndExclusive)) {
                totals.merge(log.getAppCategory(), log.getSessionDurationSec() / 60.0, Double::sum);
            }
        }
        return totals;
    }

    private FocusMetricsDto extractFocusMetrics(List<MlWeekOverWeekDeltaDto> deltas) {
        double contextSwitchRate = findCurrentValue(deltas, "context_switch_rate");
        double deepFocusMinutes = findCurrentValue(deltas, "deep_focus_minutes");
        return new FocusMetricsDto(contextSwitchRate, deepFocusMinutes);
    }

    private double findCurrentValue(List<MlWeekOverWeekDeltaDto> deltas, String metric) {
        return deltas.stream()
                .filter(d -> d.getMetric().equals(metric))
                .findFirst()
                .map(MlWeekOverWeekDeltaDto::getCurrentValue)
                .orElse(0.0);
    }

    private List<BaselineDeltaDto> mapDeltas(List<MlWeekOverWeekDeltaDto> deltas) {
        return deltas.stream()
                .map(d -> new BaselineDeltaDto(d.getMetric(), d.getCurrentValue(), d.getPreviousValue(), d.getPctChange()))
                .toList();
    }

    private String deriveTrend(List<MlWeekOverWeekDeltaDto> deltas) {
        long worseningVotes = deltas.stream()
                .filter(d -> TREND_METRICS_WORSEN_IF_UP.containsKey(d.getMetric()))
                .filter(d -> {
                    boolean worsenIfUp = TREND_METRICS_WORSEN_IF_UP.get(d.getMetric());
                    return worsenIfUp == (d.getPctChange() > 0);
                })
                .count();

        if (worseningVotes >= 3) {
            return "increasing";
        }
        if (worseningVotes <= 1) {
            return "decreasing";
        }
        return "stable";
    }
}
