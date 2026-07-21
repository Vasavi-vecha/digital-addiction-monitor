"""Unit tests for scoring.py's pure functions -- no model artifacts needed."""

from __future__ import annotations

import pandas as pd
import pytest

import scoring


def test_fit_normalization_bounds_constant_column_avoids_zero_width():
    df = pd.DataFrame({"x": [5.0] * 20})
    bounds = scoring.fit_normalization_bounds(df, ["x"])
    lo, hi = bounds["x"]
    assert lo == pytest.approx(5.0)
    assert hi == pytest.approx(5.000001)
    assert hi > lo


def test_fit_normalization_bounds_matches_pandas_quantiles():
    df = pd.DataFrame({"x": range(100)})
    bounds = scoring.fit_normalization_bounds(df, ["x"])
    lo, hi = bounds["x"]
    assert lo == pytest.approx(df["x"].quantile(0.01))
    assert hi == pytest.approx(df["x"].quantile(0.99))


def test_normalize_column_clips_to_unit_range():
    df = pd.DataFrame({"x": [-5, 0, 5, 10, 15]})
    bounds = {"x": (0, 10)}
    result = scoring.normalize_column(df, "x", bounds)
    assert list(result) == pytest.approx([0.0, 0.0, 0.5, 1.0, 1.0])


def _risk_row(**overrides) -> pd.DataFrame:
    row = {
        "context_switch_rate": 50.0,
        "night_ratio": 50.0,
        "unlock_count_week": 50.0,
        "deep_focus_minutes": 50.0,
        "abs_weekend_delta": 50.0,
        "total_screen_min_zscore": 0.0,
        "night_ratio_zscore": 0.0,
        "context_switch_rate_zscore": 0.0,
        "unlock_count_week_zscore": 0.0,
    }
    row.update(overrides)
    return pd.DataFrame([row])


def _bounds_0_100():
    cols = ["context_switch_rate", "night_ratio", "unlock_count_week", "deep_focus_minutes", "abs_weekend_delta"]
    return {c: (0.0, 100.0) for c in cols}


def test_compute_risk_score_midpoint_inputs_give_fifty():
    df = _risk_row()
    result = scoring.compute_risk_score(df, _bounds_0_100())
    assert result.iloc[0] == pytest.approx(50.0, abs=1e-6)


def test_compute_risk_score_worst_case_abs_component():
    # All abs-component drivers maxed out, deep_focus_minutes at its floor
    # (deep focus is protective, so low deep-focus = high risk contribution).
    df = _risk_row(
        context_switch_rate=100.0, night_ratio=100.0, unlock_count_week=100.0,
        deep_focus_minutes=0.0, abs_weekend_delta=100.0,
    )
    result = scoring.compute_risk_score(df, _bounds_0_100())
    # abs_component = 1.0, personal_component = 0.5 (zscores all 0)
    # risk = 100 * (0.55*1.0 + 0.45*0.5) = 77.5
    assert result.iloc[0] == pytest.approx(77.5, abs=1e-6)


def test_compute_risk_score_best_case_abs_component():
    df = _risk_row(
        context_switch_rate=0.0, night_ratio=0.0, unlock_count_week=0.0,
        deep_focus_minutes=100.0, abs_weekend_delta=0.0,
    )
    result = scoring.compute_risk_score(df, _bounds_0_100())
    # abs_component = 0.0, personal_component = 0.5
    # risk = 100 * (0.55*0 + 0.45*0.5) = 22.5
    assert result.iloc[0] == pytest.approx(22.5, abs=1e-6)


def test_compute_risk_score_high_personal_zscores_push_risk_up():
    df = _risk_row(
        total_screen_min_zscore=10.0, night_ratio_zscore=10.0,
        context_switch_rate_zscore=10.0, unlock_count_week_zscore=10.0,
    )
    result = scoring.compute_risk_score(df, _bounds_0_100())
    # abs_component = 0.5 (midpoint), personal_component -> sigmoid(10) ~= 1.0
    assert result.iloc[0] > 50.0
    assert result.iloc[0] <= 100.0


def test_compute_risk_score_always_within_0_100():
    df = _risk_row(
        context_switch_rate=1e6, night_ratio=-1e6, unlock_count_week=1e6,
        deep_focus_minutes=-1e6, abs_weekend_delta=1e6,
        total_screen_min_zscore=1e6, night_ratio_zscore=-1e6,
        context_switch_rate_zscore=1e6, unlock_count_week_zscore=-1e6,
    )
    result = scoring.compute_risk_score(df, _bounds_0_100())
    assert 0.0 <= result.iloc[0] <= 100.0


def test_compute_risk_score_missing_zscores_treated_as_zero():
    df = _risk_row()
    df.loc[0, "total_screen_min_zscore"] = float("nan")
    df.loc[0, "night_ratio_zscore"] = float("nan")
    result = scoring.compute_risk_score(df, _bounds_0_100())
    assert result.iloc[0] == pytest.approx(50.0, abs=1e-6)


def test_trailing_slope_linear_series_no_nan_window():
    s = pd.Series([10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0])
    result = scoring.trailing_slope(s, window=4)
    # first two entries can't have >=2 non-null shifted values yet
    assert pd.isna(result.iloc[0])
    assert pd.isna(result.iloc[1])
    # by index 4 the trailing window [10,20,30,40] is fully realized -> slope 10/week
    assert result.iloc[4] == pytest.approx(10.0)
    assert result.iloc[7] == pytest.approx(10.0)


def test_add_lag_features_shifts_within_user_group_only():
    df = pd.DataFrame({
        "user_id": ["u2", "u1", "u1", "u2", "u1"],
        "week": [1, 2, 0, 0, 1],
        "context_switch_rate": [21.0, 12.0, 10.0, 20.0, 11.0],
        "night_ratio": [0.0] * 5,
        "unlock_count_week": [0.0] * 5,
        "deep_focus_minutes": [0.0] * 5,
        "abs_weekend_delta": [0.0] * 5,
    })
    result = scoring.add_lag_features(df)

    u1_week2 = result[(result["user_id"] == "u1") & (result["week"] == 2)].iloc[0]
    assert u1_week2["context_switch_rate_lag1"] == pytest.approx(11.0)
    assert u1_week2["context_switch_rate_lag2"] == pytest.approx(10.0)

    u2_week1 = result[(result["user_id"] == "u2") & (result["week"] == 1)].iloc[0]
    assert u2_week1["context_switch_rate_lag1"] == pytest.approx(20.0)
    assert pd.isna(u2_week1["context_switch_rate_lag2"])

    u1_week0 = result[(result["user_id"] == "u1") & (result["week"] == 0)].iloc[0]
    assert pd.isna(u1_week0["context_switch_rate_lag1"])
    assert pd.isna(u1_week0["context_switch_rate_lag2"])


def test_lag_feature_columns_count_and_uniqueness():
    cols = scoring.lag_feature_columns()
    assert len(cols) == len(scoring.RISK_LAG_RAW_COLS) * len(scoring.RISK_LAG_STEPS)
    assert len(cols) == len(set(cols))


def test_add_polynomial_interactions_squares_and_products():
    base_cols = scoring.POLY_BASE_COLS
    row = {c: float(i + 1) for i, c in enumerate(base_cols)}
    df = pd.DataFrame([row])

    result = scoring.add_polynomial_interactions(df)

    assert result[f"{base_cols[0]}_sq"].iloc[0] == pytest.approx(1.0)
    assert result[f"{base_cols[1]}_sq"].iloc[0] == pytest.approx(4.0)
    assert result[f"{base_cols[0]}_x_{base_cols[1]}"].iloc[0] == pytest.approx(2.0)

    new_cols = set(result.columns) - set(df.columns) - set(base_cols)
    # original df already had base_cols, so subtract those too
    added = set(result.columns) - set(row.keys())
    assert added == set(scoring.poly_feature_columns())


def test_poly_feature_columns_count_matches_combinatorics():
    n = len(scoring.POLY_BASE_COLS)
    expected = n + (n * (n - 1)) // 2  # n squares + C(n,2) pairwise products
    cols = scoring.poly_feature_columns()
    assert len(cols) == expected
    assert len(cols) == len(set(cols))


def test_risk_feature_columns_total_count_and_uniqueness():
    from data.features import BASELINE_METRIC_COLUMNS

    cols = scoring.risk_feature_columns()
    expected_len = (
        len(BASELINE_METRIC_COLUMNS) * 2  # baseline mean + std
        + len(scoring.RISK_TREND_METRICS)
        + len(scoring.lag_feature_columns())
        + len(scoring.poly_feature_columns())
    )
    assert len(cols) == expected_len
    assert len(cols) == len(set(cols))
