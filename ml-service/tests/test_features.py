"""Tests for data/features.py's build_weekly_features pipeline, driven by
small synthetic fixtures from data/generator.py (cheap, deterministic, no
CSV I/O needed)."""

from __future__ import annotations

import pandas as pd
import pytest

from data.features import BASELINE_METRIC_COLUMNS, build_weekly_features
from data.generator import generate


@pytest.fixture(scope="module")
def small_weekly():
    logs_df, _ = generate(n_users=2, n_weeks=6, seed=123)
    return build_weekly_features(logs_df)


def test_expected_columns_present(small_weekly):
    expected = {
        "user_id", "week", "total_screen_min", "night_ratio", "unlock_count_week",
        "mean_session_sec", "median_session_sec", "max_session_sec", "context_switch_rate",
        "n_sessions", "deep_focus_block_count", "deep_focus_minutes", "weekend_weekday_delta_pct",
        "screen_min_social", "screen_min_productivity", "screen_min_entertainment",
        "screen_min_education", "screen_min_other",
    }
    assert expected.issubset(set(small_weekly.columns))

    for metric in BASELINE_METRIC_COLUMNS:
        for suffix in ("_baseline_mean", "_baseline_std", "_pct_change", "_zscore"):
            assert f"{metric}{suffix}" in small_weekly.columns


def test_week_index_monotonic_per_user(small_weekly):
    for user_id, group in small_weekly.groupby("user_id"):
        weeks = group["week"].tolist()
        assert weeks == sorted(weeks)
        assert len(weeks) == len(set(weeks))  # no duplicate week rows per user


def test_first_week_baseline_is_nan_strictly_prior_weeks_only(small_weekly):
    # add_personal_baseline_features shift(1)s before rolling, so a user's
    # very first observed week can have no trailing baseline yet.
    for user_id, group in small_weekly.groupby("user_id"):
        first_row = group.sort_values("week").iloc[0]
        for metric in BASELINE_METRIC_COLUMNS:
            assert pd.isna(first_row[f"{metric}_baseline_std"])
            assert pd.isna(first_row[f"{metric}_zscore"])


def test_later_week_baseline_is_populated_when_enough_history(small_weekly):
    for user_id, group in small_weekly.groupby("user_id"):
        group_sorted = group.sort_values("week")
        if len(group_sorted) < 3:
            continue
        third_row = group_sorted.iloc[2]  # 2 prior weeks realized -> std defined (min_periods=2)
        for metric in BASELINE_METRIC_COLUMNS:
            assert pd.notna(third_row[f"{metric}_baseline_mean"])
            assert pd.notna(third_row[f"{metric}_baseline_std"])


def test_night_ratio_and_context_switch_rate_are_non_negative(small_weekly):
    assert (small_weekly["night_ratio"] >= 0).all()
    assert (small_weekly["night_ratio"] <= 1).all()
    assert (small_weekly["context_switch_rate"] >= 0).all()
