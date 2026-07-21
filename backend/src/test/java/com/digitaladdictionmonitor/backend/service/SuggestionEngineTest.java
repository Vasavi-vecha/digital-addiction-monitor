package com.digitaladdictionmonitor.backend.service;

import com.digitaladdictionmonitor.backend.client.dto.MlBehaviouralDriverDto;
import com.digitaladdictionmonitor.backend.client.dto.MlWeekOverWeekDeltaDto;
import com.digitaladdictionmonitor.backend.dto.SuggestionDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionEngineTest {

    private final SuggestionEngine engine = new SuggestionEngine();

    @Test
    void nullTopDriversProducesNoSuggestions() {
        assertThat(engine.generate(null, null)).isEmpty();
    }

    @Test
    void emptyTopDriversProducesNoSuggestions() {
        assertThat(engine.generate(List.of(), List.of())).isEmpty();
    }

    @Test
    void keepsOnlyTopThreeSortedByAbsoluteZscore() {
        List<MlBehaviouralDriverDto> drivers = List.of(
                new MlBehaviouralDriverDto("night_ratio", "low z", 0.5),
                new MlBehaviouralDriverDto("unlock_count_week", "high z", -3.0),
                new MlBehaviouralDriverDto("context_switch_rate", "mid z", 2.0),
                new MlBehaviouralDriverDto("n_sessions", "highest z", 4.0)
        );

        List<SuggestionDto> suggestions = engine.generate(drivers, List.of());

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions).extracting(SuggestionDto::getMetric)
                .containsExactly("n_sessions", "unlock_count_week", "context_switch_rate");
        assertThat(suggestions).extracting(SuggestionDto::getExpectedImpact)
                .containsExactly(4.0, 3.0, 2.0);
    }

    @Test
    void worseningUpMetricWithPositiveZscoreGetsActionableMessage() {
        // night_ratio's template worsens UP; positive zscore means actual direction is UP -> worsening
        MlBehaviouralDriverDto driver = new MlBehaviouralDriverDto("night_ratio", "desc", 1.8);

        List<SuggestionDto> suggestions = engine.generate(List.of(driver), List.of());

        String message = suggestions.get(0).getMessage();
        assertThat(message).contains("try charging your phone outside the bedroom tonight");
        assertThat(message).doesNotContain("Keep it up");
    }

    @Test
    void improvingUpMetricWithNegativeZscoreGetsSupportiveMessage() {
        // night_ratio worsens UP; negative zscore means actual direction is DOWN -> improving
        MlBehaviouralDriverDto driver = new MlBehaviouralDriverDto("night_ratio", "desc", -1.8);

        List<SuggestionDto> suggestions = engine.generate(List.of(driver), List.of());

        assertThat(suggestions.get(0).getMessage()).contains("nice, that's better than your own average");
    }

    @Test
    void worseningDownMetricWithNegativeZscoreGetsActionableMessage() {
        // deep_focus_minutes worsens DOWN; negative zscore means actual direction is DOWN -> worsening
        MlBehaviouralDriverDto driver = new MlBehaviouralDriverDto("deep_focus_minutes", "desc", -2.0);

        List<SuggestionDto> suggestions = engine.generate(List.of(driver), List.of());

        assertThat(suggestions.get(0).getMessage())
                .contains("try blocking one recurring 30-minute slot for uninterrupted focus work");
    }

    @Test
    void improvingDownMetricWithPositiveZscoreGetsSupportiveMessage() {
        // deep_focus_minutes worsens DOWN; positive zscore means actual direction is UP -> improving
        MlBehaviouralDriverDto driver = new MlBehaviouralDriverDto("deep_focus_minutes", "desc", 2.0);

        List<SuggestionDto> suggestions = engine.generate(List.of(driver), List.of());

        assertThat(suggestions.get(0).getMessage()).contains("nice, that's better than your own average");
    }

    @Test
    void unknownMetricFallsBackToGenericTemplate() {
        MlBehaviouralDriverDto driver = new MlBehaviouralDriverDto("some_new_metric", "desc", 1.0);

        List<SuggestionDto> suggestions = engine.generate(List.of(driver), List.of());

        assertThat(suggestions.get(0).getMessage())
                .contains("try keeping an eye on it for a week to see if a small, deliberate change helps");
    }

    @Test
    void evidenceClauseUsesWeekOverWeekDeltaWhenPreviousValueNonZero() {
        MlBehaviouralDriverDto driver = new MlBehaviouralDriverDto("total_screen_min", "desc", 1.5);
        MlWeekOverWeekDeltaDto delta = new MlWeekOverWeekDeltaDto("total_screen_min", 360.0, 300.0, 20.0);

        List<SuggestionDto> suggestions = engine.generate(List.of(driver), List.of(delta));

        assertThat(suggestions.get(0).getMessage())
                .startsWith("Your total screen time rose 20% this week vs your own last 4-week average");
    }

    @Test
    void evidenceClauseFallsBackToZscorePhrasingWhenDeltaMissing() {
        MlBehaviouralDriverDto driver = new MlBehaviouralDriverDto("total_screen_min", "desc", 1.5);

        List<SuggestionDto> suggestions = engine.generate(List.of(driver), List.of());

        assertThat(suggestions.get(0).getMessage())
                .startsWith("Your total screen time is higher than your own last-4-week average this week (z=+1.50)");
    }

    @Test
    void evidenceClauseFallsBackToZscorePhrasingWhenPreviousValueIsZero() {
        MlBehaviouralDriverDto driver = new MlBehaviouralDriverDto("total_screen_min", "desc", -1.5);
        MlWeekOverWeekDeltaDto delta = new MlWeekOverWeekDeltaDto("total_screen_min", 60.0, 0.0, 0.0);

        List<SuggestionDto> suggestions = engine.generate(List.of(driver), List.of(delta));

        assertThat(suggestions.get(0).getMessage())
                .startsWith("Your total screen time is lower than your own last-4-week average this week (z=-1.50)");
    }
}
