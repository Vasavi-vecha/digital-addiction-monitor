"""Shared risk-scoring and clustering feature definitions.

Both train.py (fits models offline) and app/ (serves them) import from here.
The composite risk-score formula and the cluster feature set must be
identical at train time and serve time -- defining them in one place instead
of duplicating the formula in the FastAPI layer rules out train/serve drift,
a common and hard-to-notice class of ML bug.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from data.features import BASELINE_METRIC_COLUMNS

RISK_TREND_METRICS = ["total_screen_min", "night_ratio", "context_switch_rate", "deep_focus_minutes", "unlock_count_week"]
MIN_TRAILING_WEEKS_FOR_RISK = 4  # need a full 4-week trailing window
RISK_ZSCORE_COLS = ["total_screen_min_zscore", "night_ratio_zscore", "context_switch_rate_zscore", "unlock_count_week_zscore"]
RISK_ABS_COLS = ["context_switch_rate", "night_ratio", "unlock_count_week", "deep_focus_minutes", "abs_weekend_delta"]

CLUSTER_FEATURE_COLS = [
    "total_screen_min", "screen_min_social", "screen_min_productivity", "screen_min_entertainment",
    "screen_min_education", "screen_min_other", "night_ratio", "context_switch_rate", "unlock_count_week",
    "mean_session_sec", "median_session_sec", "max_session_sec", "n_sessions",
    "deep_focus_block_count", "deep_focus_minutes", "weekend_weekday_delta_pct",
]


def fit_normalization_bounds(df: pd.DataFrame, cols: list[str]) -> dict:
    bounds = {}
    for c in cols:
        lo, hi = df[c].quantile([0.01, 0.99])
        bounds[c] = (float(lo), float(hi) if hi > lo else float(lo) + 1e-6)
    return bounds


def normalize_column(df: pd.DataFrame, col: str, bounds: dict) -> pd.Series:
    lo, hi = bounds[col]
    return ((df[col] - lo) / (hi - lo)).clip(0, 1)


def compute_risk_score(df: pd.DataFrame, bounds: dict) -> pd.Series:
    """0-100 composite risk score for a week, built from that week's own
    behaviour. This is the regression TARGET at train time and the
    descriptive score returned by /analyze/weekly at serve time -- it
    deliberately weights personal-baseline deviation (45%) as heavily as
    population-relative severity (55%), because the project's whole thesis
    is that risk means "worse than your own history", not "past a universal
    cutoff".

    `bounds` (1st/99th percentile per column) must be fit once on training
    data and reused everywhere after -- fitting bounds on whatever data is
    passed in would let each caller's own distribution leak into the
    normalization.
    """
    abs_component = (
        0.25 * normalize_column(df, "context_switch_rate", bounds)
        + 0.25 * normalize_column(df, "night_ratio", bounds)
        + 0.20 * normalize_column(df, "unlock_count_week", bounds)
        + 0.20 * (1 - normalize_column(df, "deep_focus_minutes", bounds))
        + 0.10 * normalize_column(df, "abs_weekend_delta", bounds)
    )
    personal = df[RISK_ZSCORE_COLS].fillna(0)
    personal_component = (1 / (1 + np.exp(-personal))).mean(axis=1)

    risk = 100 * (0.55 * abs_component + 0.45 * personal_component)
    return risk.clip(0, 100)


def trailing_slope(s: pd.Series, window: int = 4) -> pd.Series:
    def slope(values: np.ndarray) -> float:
        if len(values) < 2:
            return 0.0
        x = np.arange(len(values))
        return np.polyfit(x, values, 1)[0]

    return s.shift(1).rolling(window, min_periods=2).apply(slope, raw=True)


# 1- and 2-week-lagged raw values of the metrics that directly drive the
# target's abs_component (RISK_ABS_COLS). Restricted to these -- rather than
# every BASELINE_METRIC_COLUMN -- because /predict/risk only ever receives 4
# prior weeks: lag1/lag2 of a *raw* metric are recoverable from that window
# (they're just the last two of the four weeks given), but a lagged
# *z-score* would need a trailing baseline for week w-1/w-2 in turn, i.e. a
# 5th prior week the API doesn't have. Using the raw current-week zscore
# columns already in weekly_features.csv as features was considered and
# rejected: that zscore is literally built from this week's own value (see
# compute_risk_score's personal_component), so it would leak the target.
RISK_LAG_STEPS = (1, 2)
RISK_LAG_RAW_COLS = RISK_ABS_COLS


def add_lag_features(df: pd.DataFrame) -> pd.DataFrame:
    """Add `{col}_lag1`/`{col}_lag2` for RISK_LAG_RAW_COLS. Each lag is a
    groupby(user_id).shift(k>=1) of an already-realized past week's value, so
    it carries no information about the current week -- same leakage
    guarantee as baseline_mean/std/trend_slope, just at finer granularity."""
    df = df.sort_values(["user_id", "week"]).reset_index(drop=True)
    for col in RISK_LAG_RAW_COLS:
        for lag in RISK_LAG_STEPS:
            df[f"{col}_lag{lag}"] = df.groupby("user_id")[col].shift(lag)
    return df


def lag_feature_columns() -> list[str]:
    return [f"{col}_lag{lag}" for col in RISK_LAG_RAW_COLS for lag in RISK_LAG_STEPS]


# Top 8 base features by |correlation| with the risk-score target, measured
# on the train split (weeks 4-8, seed=42) -- see train.py's risk-regression
# comment for the actual correlation values. Fixed as a constant (rather than
# re-selected each training run) so /predict/risk can build the exact same
# interaction columns at serve time without needing to know how they were
# chosen.
POLY_BASE_COLS = [
    "context_switch_rate_baseline_mean",
    "night_ratio_baseline_mean",
    "mean_session_sec_baseline_mean",
    "unlock_count_week_baseline_mean",
    "median_session_sec_baseline_mean",
    "n_sessions_baseline_mean",
    "deep_focus_block_count_baseline_mean",
    "deep_focus_minutes_baseline_mean",
]


def add_polynomial_interactions(df: pd.DataFrame, cols: list[str] = POLY_BASE_COLS) -> pd.DataFrame:
    """Squared + pairwise-interaction terms for `cols`. Purely deterministic
    column arithmetic on already-safe inputs -- adds no new information
    source, just lets a linear model fit curvature/co-movement between the
    strongest predictors instead of only their independent linear effects."""
    df = df.copy()
    for c in cols:
        df[f"{c}_sq"] = df[c] ** 2
    for i, c1 in enumerate(cols):
        for c2 in cols[i + 1:]:
            df[f"{c1}_x_{c2}"] = df[c1] * df[c2]
    return df


def poly_feature_columns(cols: list[str] = POLY_BASE_COLS) -> list[str]:
    sq = [f"{c}_sq" for c in cols]
    inter = [f"{c1}_x_{c2}" for i, c1 in enumerate(cols) for c2 in cols[i + 1:]]
    return sq + inter


def build_risk_dataset(weekly_df: pd.DataFrame) -> pd.DataFrame:
    df = weekly_df.copy().sort_values(["user_id", "week"]).reset_index(drop=True)
    df["abs_weekend_delta"] = df["weekend_weekday_delta_pct"].abs()

    for m in RISK_TREND_METRICS:
        df[f"{m}_trend_slope"] = df.groupby("user_id")[m].transform(lambda s: trailing_slope(s))

    df = add_lag_features(df)
    df = add_polynomial_interactions(df)

    return df[df["week"] >= MIN_TRAILING_WEEKS_FOR_RISK].reset_index(drop=True)


def risk_feature_columns() -> list[str]:
    baseline_cols = [f"{m}_baseline_mean" for m in BASELINE_METRIC_COLUMNS] + [
        f"{m}_baseline_std" for m in BASELINE_METRIC_COLUMNS
    ]
    trend_cols = [f"{m}_trend_slope" for m in RISK_TREND_METRICS]
    return baseline_cols + trend_cols + lag_feature_columns() + poly_feature_columns()
