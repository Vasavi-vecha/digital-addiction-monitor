"""POST /analyze/weekly: turn one user's raw session logs into a weekly
report -- risk score, cluster persona, top behavioural drivers, and
week-over-week deltas.

Unlike /predict/risk (which forecasts a week that hasn't happened yet from
the prior 4), this analyzes a week that has already concluded: the risk
score is computed directly from that week's own behaviour via the same
compute_risk_score formula used as the training target, using the
population bounds fit once at training time (never refit per request, to
keep the score comparable across users and over time).
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from app.artifacts import Artifacts
from app.schemas import (
    AnalyzeWeeklyRequest,
    AnalyzeWeeklyResponse,
    BehaviouralDriver,
    WeekOverWeekDelta,
)
from data.features import BASELINE_METRIC_COLUMNS, build_weekly_features
from scoring import CLUSTER_FEATURE_COLS, compute_risk_score

WOW_METRICS = [
    "total_screen_min", "night_ratio", "context_switch_rate",
    "deep_focus_minutes", "unlock_count_week", "weekend_weekday_delta_pct",
]
TOP_N_DRIVERS = 3


def _driver_description(metric: str, row: pd.Series) -> str:
    current = row[metric]
    baseline_mean = row.get(f"{metric}_baseline_mean")
    pct_change = row.get(f"{metric}_pct_change")
    z = row.get(f"{metric}_zscore")

    if pd.isna(pct_change) or pd.isna(baseline_mean):
        return f"{metric} = {current:.2f} (no personal baseline yet)"

    direction = "up" if pct_change >= 0 else "down"
    return (
        f"{metric} {direction} {abs(pct_change):.0f}% vs your 4-week average "
        f"({baseline_mean:.2f} -> {current:.2f}), z={z:+.2f}"
    )


def analyze_weekly(req: AnalyzeWeeklyRequest, artifacts: Artifacts) -> AnalyzeWeeklyResponse:
    if not req.logs:
        raise ValueError("logs must contain at least one row")

    logs_df = pd.DataFrame([row.model_dump() for row in req.logs])
    logs_df = logs_df[logs_df["user_id"] == req.user_id]
    if logs_df.empty:
        raise ValueError(f"no log rows found for user_id={req.user_id!r}")

    weekly = build_weekly_features(logs_df)
    if weekly.empty:
        raise ValueError("no weekly data could be derived from the supplied logs")

    # compute_risk_score expects abs_weekend_delta, normally added by
    # scoring.build_risk_dataset() at training time -- add it here too so
    # the exact same formula applies at serve time.
    weekly["abs_weekend_delta"] = weekly["weekend_weekday_delta_pct"].abs()

    current = weekly.iloc[-1]
    previous = weekly.iloc[-2] if len(weekly) >= 2 else None

    risk_score = float(compute_risk_score(weekly.iloc[[-1]], artifacts.risk_score_bounds).iloc[0])

    cluster_features = current[CLUSTER_FEATURE_COLS].fillna(0).to_numpy().reshape(1, -1)
    cluster_scaled = artifacts.behavior_scaler.transform(cluster_features)
    cluster_id = int(artifacts.behavior_kmeans.predict(cluster_scaled)[0])
    persona = artifacts.cluster_persona_map.get(cluster_id, f"cluster_{cluster_id}")

    zscore_cols = [f"{m}_zscore" for m in BASELINE_METRIC_COLUMNS]
    zscores = current[zscore_cols].dropna()
    top_metrics = zscores.abs().sort_values(ascending=False).head(TOP_N_DRIVERS).index
    top_drivers = [
        BehaviouralDriver(
            metric=col.removesuffix("_zscore"),
            description=_driver_description(col.removesuffix("_zscore"), current),
            zscore=float(current[col]),
        )
        for col in top_metrics
    ]

    deltas: list[WeekOverWeekDelta] = []
    if previous is not None:
        for m in WOW_METRICS:
            cur_v, prev_v = float(current[m]), float(previous[m])
            pct = (cur_v - prev_v) / (abs(prev_v) + 1e-6) * 100
            deltas.append(WeekOverWeekDelta(metric=m, current_value=cur_v, previous_value=prev_v, pct_change=pct))

    return AnalyzeWeeklyResponse(
        user_id=req.user_id,
        week_index=int(current["week"]),
        risk_score=risk_score,
        cluster_persona=persona,
        top_drivers=top_drivers,
        week_over_week_deltas=deltas,
    )
