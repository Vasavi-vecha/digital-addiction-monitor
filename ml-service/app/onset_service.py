"""POST /predict/onset: probability a binge starts in the next 30 minutes.

This is a documented rule-based heuristic, not a trained classifier -- a
supervised onset model needs labeled binge-onset events, which don't exist
without a real user study. The heuristic combines the same fragmentation
signals the other two models identified as meaningful (app-switch rate,
social/entertainment share, session shrinkage, unlock rate) into a single
transparent, tunable score. Replacing it with a trained model later is a
drop-in swap once real onset labels exist -- the request/response contract
would not need to change.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from app.schemas import OnsetPredictionRequest, OnsetPredictionResponse

ONSET_WINDOW_MIN = 30
SHORT_SESSION_SEC = 120

# Saturation points for each raw signal -- values at/above this are treated
# as maximally alarming (score capped at 1.0). Chosen to be clearly above
# what a relaxed session looks like, not statistically fit.
SWITCH_RATE_SATURATION_PER_MIN = 3.0
UNLOCK_RATE_SATURATION_PER_MIN = 2.0

WEIGHT_SWITCH = 0.35
WEIGHT_ENTERTAINMENT = 0.30
WEIGHT_SHORT_SESSIONS = 0.20
WEIGHT_UNLOCK = 0.15

RISK_LEVEL_THRESHOLDS = (0.35, 0.65)  # low < 0.35 <= moderate < 0.65 <= high


def _risk_level(probability: float) -> str:
    low_hi, mod_hi = RISK_LEVEL_THRESHOLDS
    if probability < low_hi:
        return "low"
    if probability < mod_hi:
        return "moderate"
    return "high"


def estimate_onset(req: OnsetPredictionRequest) -> OnsetPredictionResponse:
    df = pd.DataFrame([s.model_dump() for s in req.recent_sessions])
    window = df[df["minutes_ago"] <= ONSET_WINDOW_MIN]
    if window.empty:
        window = df
    # chronological order (oldest first) so consecutive-row comparisons are valid
    window = window.sort_values("minutes_ago", ascending=False).reset_index(drop=True)

    total_time_min = max(window["session_duration_sec"].sum() / 60, 1e-6)
    span_min = max(window["minutes_ago"].max(), 1.0)

    ent_soc_time_min = window.loc[
        window["app_category"].isin(["social", "entertainment"]), "session_duration_sec"
    ].sum() / 60
    ent_soc_share = ent_soc_time_min / total_time_min

    n_switches = int((window["app_category"] != window["app_category"].shift(1)).iloc[1:].sum())
    switch_rate_per_min = n_switches / span_min

    short_session_share = float((window["session_duration_sec"] < SHORT_SESSION_SEC).mean())

    unlock_rate_per_min = window["unlock_count"].sum() / span_min

    norm_switch = min(switch_rate_per_min / SWITCH_RATE_SATURATION_PER_MIN, 1.0)
    norm_ent = min(ent_soc_share, 1.0)
    norm_short = short_session_share
    norm_unlock = min(unlock_rate_per_min / UNLOCK_RATE_SATURATION_PER_MIN, 1.0)

    score = (
        WEIGHT_SWITCH * norm_switch
        + WEIGHT_ENTERTAINMENT * norm_ent
        + WEIGHT_SHORT_SESSIONS * norm_short
        + WEIGHT_UNLOCK * norm_unlock
    )
    probability = float(np.clip(score, 0, 1))

    drivers = []
    if norm_switch > 0.4:
        drivers.append(f"{n_switches} app switches in the last {int(span_min)} min")
    if norm_ent > 0.5:
        drivers.append(f"{ent_soc_share * 100:.0f}% of recent time on social/entertainment apps")
    if norm_short > 0.4:
        drivers.append(f"{short_session_share * 100:.0f}% of recent sessions under 2 minutes")
    if norm_unlock > 0.4:
        drivers.append(f"{unlock_rate_per_min:.1f} unlocks per minute recently")
    if not drivers:
        drivers.append("No strong binge precursors detected in recent activity")

    return OnsetPredictionResponse(
        onset_probability=probability,
        risk_level=_risk_level(probability),
        drivers=drivers,
    )
