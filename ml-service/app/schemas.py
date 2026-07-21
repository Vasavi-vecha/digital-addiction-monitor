"""Pydantic request/response models for the ML service API.

Field names deliberately mirror the columns produced by data/features.py so
a caller who already has a weekly_features.csv row (or a raw usage-log
export matching generator.SCHEMA_COLUMNS) can pass it through with no
renaming.
"""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator

AppCategory = Literal["social", "productivity", "entertainment", "education", "other"]
PrevAppCategory = Literal["social", "productivity", "entertainment", "education", "other", "none"]
FocusLabel = Literal["focused", "distracted", "passive"]
TrendDirection = Literal["increasing", "decreasing", "stable"]
RiskLevel = Literal["low", "moderate", "high"]


# ---------------------------------------------------------------------------
# POST /predict/focus
# ---------------------------------------------------------------------------
class SessionWindowRequest(BaseModel):
    app_category: AppCategory
    session_duration_sec: float = Field(ge=0)
    hour: int = Field(ge=0, le=23)
    is_weekend: bool
    is_night_usage: bool
    unlock_count: int = Field(ge=0, default=0)
    gap_before_sec: float | None = Field(
        default=None, description="Seconds since the previous session ended; null if unknown or first of the day."
    )
    prev_app_category: PrevAppCategory = "none"
    recent_switch_count_15min: int = Field(ge=0, default=0)


class FocusPredictionResponse(BaseModel):
    label: FocusLabel
    confidence: float
    probabilities: dict[str, float]


# ---------------------------------------------------------------------------
# POST /predict/risk
# ---------------------------------------------------------------------------
class WeeklySnapshot(BaseModel):
    total_screen_min: float
    screen_min_social: float
    screen_min_productivity: float
    screen_min_entertainment: float
    screen_min_education: float
    screen_min_other: float
    night_ratio: float
    unlock_count_week: float
    mean_session_sec: float
    median_session_sec: float
    max_session_sec: float
    context_switch_rate: float
    n_sessions: float
    deep_focus_block_count: float
    deep_focus_minutes: float
    weekend_weekday_delta_pct: float


class RiskPredictionRequest(BaseModel):
    weekly_history: list[WeeklySnapshot] = Field(
        description="Exactly 4 prior weeks, oldest first (week w-4 .. w-1), used to forecast week w's risk."
    )

    @field_validator("weekly_history")
    @classmethod
    def _exactly_four_weeks(cls, v: list[WeeklySnapshot]) -> list[WeeklySnapshot]:
        if len(v) != 4:
            raise ValueError(f"weekly_history must contain exactly 4 weeks, got {len(v)}")
        return v


class RiskPredictionResponse(BaseModel):
    predicted_risk_score: float
    trend_direction: TrendDirection
    contributing_trend_slopes: dict[str, float]


# ---------------------------------------------------------------------------
# POST /predict/onset
# ---------------------------------------------------------------------------
class RecentSession(BaseModel):
    app_category: AppCategory
    session_duration_sec: float = Field(ge=0)
    minutes_ago: float = Field(ge=0, description="Minutes before now this session started; 0 = most recent.")
    unlock_count: int = Field(ge=0, default=0)


class OnsetPredictionRequest(BaseModel):
    recent_sessions: list[RecentSession] = Field(min_length=2, max_length=100)


class OnsetPredictionResponse(BaseModel):
    onset_probability: float
    risk_level: RiskLevel
    drivers: list[str]


# ---------------------------------------------------------------------------
# POST /analyze/weekly
# ---------------------------------------------------------------------------
class UsageLogRow(BaseModel):
    user_id: str
    timestamp: datetime
    app_name: str
    app_category: AppCategory | None = None
    session_duration_sec: float = Field(ge=0)
    unlock_count: int = Field(ge=0, default=0)
    is_night_usage: bool


class AnalyzeWeeklyRequest(BaseModel):
    user_id: str
    logs: list[UsageLogRow] = Field(
        description="Raw session rows for this user, ideally spanning >=5 weeks "
        "(>=4 for the personal baseline, plus the week being analyzed)."
    )


class BehaviouralDriver(BaseModel):
    metric: str
    description: str
    zscore: float


class WeekOverWeekDelta(BaseModel):
    metric: str
    current_value: float
    previous_value: float
    pct_change: float


class AnalyzeWeeklyResponse(BaseModel):
    user_id: str
    week_index: int
    risk_score: float
    cluster_persona: str
    top_drivers: list[BehaviouralDriver]
    week_over_week_deltas: list[WeekOverWeekDelta]


# ---------------------------------------------------------------------------
# GET /health
# ---------------------------------------------------------------------------
class HealthResponse(BaseModel):
    status: Literal["ok"]
    models_loaded: list[str]
