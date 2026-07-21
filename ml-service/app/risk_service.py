"""POST /predict/risk: forecast next week's 0-100 addiction-risk score from
the prior 4 weeks.

The 4 provided weeks are turned into the exact same baseline-mean/std,
trend-slope, lag1/lag2, and polynomial/interaction features the regressor
was trained on (see scoring.py / train.py's risk_feature_columns()). Trend
direction is derived separately, directly from the sign of the 5 trend
slopes, rather than by comparing the prediction to a recomputed "previous
week" risk score -- that would need a 5th week of history just to get *its*
personal-baseline z-score right, which the API doesn't have. The same 4-week
ceiling is why lag features only go back to raw values (lag1/lag2 of
RISK_ABS_COLS, i.e. just the last two of the four given weeks) rather than
lagged z-scores, which would need a trailing baseline for week w-1/w-2 in
turn -- see scoring.py's RISK_LAG_RAW_COLS comment.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from app.artifacts import Artifacts
from app.schemas import RiskPredictionRequest, RiskPredictionResponse
from data.features import BASELINE_METRIC_COLUMNS
from scoring import RISK_ABS_COLS, RISK_LAG_STEPS, RISK_TREND_METRICS, add_polynomial_interactions

TREND_EPS = 1e-6


def _build_features(req: RiskPredictionRequest) -> dict[str, float]:
    df = pd.DataFrame([w.model_dump() for w in req.weekly_history])  # oldest -> newest, 4 rows
    df["abs_weekend_delta"] = df["weekend_weekday_delta_pct"].abs()

    features: dict[str, float] = {}
    for m in BASELINE_METRIC_COLUMNS:
        vals = df[m].to_numpy(dtype=float)
        features[f"{m}_baseline_mean"] = float(np.mean(vals))
        features[f"{m}_baseline_std"] = float(np.std(vals, ddof=1))

    for m in RISK_TREND_METRICS:
        vals = df[m].to_numpy(dtype=float)
        x = np.arange(len(vals))
        features[f"{m}_trend_slope"] = float(np.polyfit(x, vals, 1)[0])

    # lag1/lag2: the last two of the four provided weeks, oldest->newest, so
    # vals[-1] is the week immediately before the forecast target (lag1) and
    # vals[-2] is two weeks before it (lag2) -- matches
    # scoring.add_lag_features' groupby(...).shift(1)/shift(2) exactly.
    for col in RISK_ABS_COLS:
        vals = df[col].to_numpy(dtype=float)
        for lag in RISK_LAG_STEPS:
            features[f"{col}_lag{lag}"] = float(vals[-lag])

    poly_row = add_polynomial_interactions(pd.DataFrame([features]))
    features.update(poly_row.iloc[0].to_dict())

    return features


def _trend_direction(features: dict[str, float]) -> str:
    worsening_votes = 0
    for m in RISK_TREND_METRICS:
        slope = features[f"{m}_trend_slope"]
        if m == "deep_focus_minutes":
            worsening_votes += slope < -TREND_EPS
        else:
            worsening_votes += slope > TREND_EPS

    if worsening_votes >= 3:
        return "increasing"
    if worsening_votes <= 1:
        return "decreasing"
    return "stable"


def predict_risk(req: RiskPredictionRequest, artifacts: Artifacts) -> RiskPredictionResponse:
    features = _build_features(req)
    X = pd.DataFrame([features]).reindex(columns=artifacts.risk_feature_cols, fill_value=0)

    predicted = float(np.clip(artifacts.risk_regressor.predict(X)[0], 0, 100))
    trend_slopes = {m: features[f"{m}_trend_slope"] for m in RISK_TREND_METRICS}

    return RiskPredictionResponse(
        predicted_risk_score=predicted,
        trend_direction=_trend_direction(features),
        contributing_trend_slopes=trend_slopes,
    )
