package com.digitaladdictionmonitor.backend.service;

import com.digitaladdictionmonitor.backend.client.dto.MlBehaviouralDriverDto;
import com.digitaladdictionmonitor.backend.client.dto.MlWeekOverWeekDeltaDto;
import com.digitaladdictionmonitor.backend.dto.SuggestionDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns ml-service's top personal-baseline drivers into concrete, supportive
 * suggestions. ml-service already picks the top drivers by |zscore| (see
 * app/weekly_service.py) and ranking here preserves that same ordering, since
 * "further from your own normal pattern" is what "expected impact" means for
 * this project's personal-baseline thesis.
 *
 * A driver can point either way: a metric can be a *worse* deviation (e.g.
 * night_ratio unusually high) or a *better* one (e.g. night_ratio unusually
 * low). Only the worsening direction gets an actionable suggestion; the
 * improving direction gets supportive acknowledgement instead -- never
 * scolding, and never a fabricated "problem" out of good behaviour.
 */
@Component
public class SuggestionEngine {

    private static final int MAX_SUGGESTIONS = 3;

    private enum Direction { UP, DOWN }

    private record Template(String label, Direction worsening, String action) {
    }

    private static final Template GENERIC = new Template("this metric", Direction.UP,
            "try keeping an eye on it for a week to see if a small, deliberate change helps");

    private static final Map<String, Template> TEMPLATES = Map.ofEntries(
            Map.entry("total_screen_min", new Template("your total screen time", Direction.UP,
                    "try setting a daily time limit on your single most-used app")),
            Map.entry("screen_min_social", new Template("your social app usage", Direction.UP,
                    "try setting a daily app timer on your top social app")),
            Map.entry("screen_min_entertainment", new Template("your entertainment app usage", Direction.UP,
                    "try swapping one entertainment session for a short walk or a call with a friend")),
            Map.entry("screen_min_productivity", new Template("your productivity app usage", Direction.DOWN,
                    "try blocking one recurring slot on your calendar for focused work")),
            Map.entry("screen_min_education", new Template("your study-app usage", Direction.DOWN,
                    "try scheduling one short daily slot for what you're learning")),
            Map.entry("night_ratio", new Template("your late-night phone use", Direction.UP,
                    "try charging your phone outside the bedroom tonight")),
            Map.entry("unlock_count_week", new Template("how often you pick up your phone", Direction.UP,
                    "try turning off non-essential notifications for your top 3 apps")),
            Map.entry("context_switch_rate", new Template("how often you switch between apps", Direction.UP,
                    "try a 25-minute focus timer to build longer unbroken sessions")),
            Map.entry("n_sessions", new Template("how many separate app sessions you have", Direction.UP,
                    "try batching quick checks into 2-3 set times a day instead of many short visits")),
            Map.entry("deep_focus_block_count", new Template("your number of deep-focus sessions", Direction.DOWN,
                    "try blocking one recurring 30-minute slot for uninterrupted focus work")),
            Map.entry("deep_focus_minutes", new Template("your deep-focus time", Direction.DOWN,
                    "try blocking one recurring 30-minute slot for uninterrupted focus work")),
            Map.entry("weekend_weekday_delta_pct", new Template("your weekend usage jump vs weekdays", Direction.UP,
                    "try planning one screen-light weekend activity, like a walk or meeting a friend in person")),
            Map.entry("screen_min_other", new Template("your time in utility apps like maps, camera or settings", Direction.UP,
                    "try noticing which of these apps is pulling extra time and batching quick checks together")),
            Map.entry("mean_session_sec", new Template("your average session length", Direction.UP,
                    "try noticing when a single app session passes 20 minutes and taking a short break")),
            Map.entry("median_session_sec", new Template("your typical session length", Direction.UP,
                    "try setting a gentle timer for sessions that run past 20 minutes")),
            Map.entry("max_session_sec", new Template("your longest single session", Direction.UP,
                    "try a screen-time reminder that nudges you once a session runs unusually long"))
    );

    public List<SuggestionDto> generate(
            List<MlBehaviouralDriverDto> topDrivers,
            List<MlWeekOverWeekDeltaDto> weekOverWeekDeltas
    ) {
        if (topDrivers == null || topDrivers.isEmpty()) {
            return List.of();
        }

        Map<String, MlWeekOverWeekDeltaDto> deltasByMetric = weekOverWeekDeltas == null
                ? Map.of()
                : weekOverWeekDeltas.stream().collect(Collectors.toMap(
                        MlWeekOverWeekDeltaDto::getMetric, Function.identity(), (a, b) -> a));

        return topDrivers.stream()
                .sorted(Comparator.comparingDouble((MlBehaviouralDriverDto d) -> Math.abs(d.getZscore())).reversed())
                .limit(MAX_SUGGESTIONS)
                .map(driver -> toSuggestion(driver, deltasByMetric.get(driver.getMetric())))
                .toList();
    }

    private SuggestionDto toSuggestion(MlBehaviouralDriverDto driver, MlWeekOverWeekDeltaDto delta) {
        Template template = TEMPLATES.getOrDefault(driver.getMetric(), GENERIC);
        boolean actualDirectionIsUp = driver.getZscore() >= 0;
        boolean isWorsening = (template.worsening() == Direction.UP) == actualDirectionIsUp;

        String evidence = buildEvidenceClause(template.label(), driver, delta);
        String message = isWorsening
                ? evidence + " — " + template.action() + "."
                : evidence + " — nice, that's better than your own average. Keep it up.";

        return new SuggestionDto(driver.getMetric(), message, Math.abs(driver.getZscore()));
    }

    private String buildEvidenceClause(String label, MlBehaviouralDriverDto driver, MlWeekOverWeekDeltaDto delta) {
        if (delta != null && delta.getPreviousValue() != 0) {
            String verb = delta.getPctChange() >= 0 ? "rose" : "fell";
            return String.format(
                    "%s %s %.0f%% this week vs your own last 4-week average",
                    capitalize(label), verb, Math.abs(delta.getPctChange())
            );
        }
        String verb = driver.getZscore() >= 0 ? "is higher" : "is lower";
        return String.format(
                "%s %s than your own last-4-week average this week (z=%+.2f)",
                capitalize(label), verb, driver.getZscore()
        );
    }

    private String capitalize(String label) {
        return label.isEmpty() ? label : Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
