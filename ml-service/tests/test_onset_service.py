"""Unit tests for the rule-based /predict/onset heuristic (app/onset_service.py).

This is a deliberate, documented design choice -- a real classifier needs
labeled binge-onset events that don't exist without a real user study (see
the module docstring). These tests hold the transparent heuristic to its own
documented formula, not to a placeholder-shaped afterthought.
"""

from __future__ import annotations

import pytest

from app.onset_service import _risk_level, estimate_onset
from app.schemas import OnsetPredictionRequest, RecentSession


def test_risk_level_boundaries():
    assert _risk_level(0.0) == "low"
    assert _risk_level(0.34) == "low"
    assert _risk_level(0.35) == "moderate"
    assert _risk_level(0.64) == "moderate"
    assert _risk_level(0.65) == "high"
    assert _risk_level(1.0) == "high"


def test_calm_session_window_is_low_risk_with_fallback_driver():
    req = OnsetPredictionRequest(recent_sessions=[
        RecentSession(app_category="productivity", session_duration_sec=900, minutes_ago=20, unlock_count=0),
        RecentSession(app_category="productivity", session_duration_sec=900, minutes_ago=5, unlock_count=0),
    ])

    result = estimate_onset(req)

    assert result.onset_probability == pytest.approx(0.0, abs=1e-9)
    assert result.risk_level == "low"
    assert result.drivers == ["No strong binge precursors detected in recent activity"]


def test_fragmented_social_binge_window_is_high_risk_with_all_drivers():
    req = OnsetPredictionRequest(recent_sessions=[
        RecentSession(app_category="entertainment", session_duration_sec=30, minutes_ago=3, unlock_count=2),
        RecentSession(app_category="social", session_duration_sec=30, minutes_ago=2.5, unlock_count=2),
        RecentSession(app_category="entertainment", session_duration_sec=30, minutes_ago=2, unlock_count=2),
        RecentSession(app_category="social", session_duration_sec=30, minutes_ago=1.5, unlock_count=2),
        RecentSession(app_category="entertainment", session_duration_sec=30, minutes_ago=1, unlock_count=2),
        RecentSession(app_category="social", session_duration_sec=30, minutes_ago=0, unlock_count=2),
    ])

    result = estimate_onset(req)

    assert result.onset_probability == pytest.approx(0.8444444444, abs=1e-6)
    assert result.risk_level == "high"
    assert "5 app switches in the last 3 min" in result.drivers
    assert "100% of recent time on social/entertainment apps" in result.drivers
    assert "100% of recent sessions under 2 minutes" in result.drivers
    assert "4.0 unlocks per minute recently" in result.drivers
    assert len(result.drivers) == 4


def test_sessions_all_outside_window_fall_back_to_full_history():
    # Both sessions are older than ONSET_WINDOW_MIN=30, so the windowed
    # slice is empty and estimate_onset must fall back to the full set
    # instead of dividing by zero / crashing.
    req = OnsetPredictionRequest(recent_sessions=[
        RecentSession(app_category="productivity", session_duration_sec=600, minutes_ago=120, unlock_count=0),
        RecentSession(app_category="productivity", session_duration_sec=600, minutes_ago=100, unlock_count=0),
    ])

    result = estimate_onset(req)

    assert 0.0 <= result.onset_probability <= 1.0
    assert result.risk_level in ("low", "moderate", "high")
    assert len(result.drivers) >= 1
