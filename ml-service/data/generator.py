"""Synthetic app-usage log generator for the Digital Addiction Monitor project.

Produces a flat table of app-usage "sessions" shaped like what a real Android
UsageStatsManager export would look like. Four personas (healthy, borderline,
high_risk, weekend_binger) are baked in with per-user jitter and week-to-week
drift so labels are learnable but not trivially separable, plus injected
noise and missing rows to mimic real-world logging gaps.

Swapping synthetic data for a real device export later means writing a new
adapter that emits a CSV with exactly SCHEMA_COLUMNS -- no changes needed in
features.py or the model training code.
"""

from __future__ import annotations

import argparse
import datetime as dt
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd

# ---------------------------------------------------------------------------
# Schema contract shared with feature engineering.
# ---------------------------------------------------------------------------
SCHEMA_COLUMNS = [
    "user_id",
    "timestamp",
    "app_name",
    "app_category",
    "session_duration_sec",
    "unlock_count",
    "is_night_usage",
]

CATEGORIES = ["social", "productivity", "entertainment", "education", "other"]

APP_POOL = {
    "social": ["Instagram", "WhatsApp", "Snapchat", "Facebook", "Twitter", "Telegram"],
    "productivity": ["Notion", "GoogleDocs", "VSCode", "Slack", "Excel", "Todoist"],
    "entertainment": ["YouTube", "Netflix", "Spotify", "TikTok", "PUBGMobile", "PrimeVideo"],
    "education": ["Coursera", "KhanAcademy", "Duolingo", "GoogleClassroom", "Kindle"],
    "other": ["Camera", "Settings", "Phone", "Maps", "Chrome", "Gallery"],
}

CATEGORY_DURATION_MEDIAN_MIN = {
    "social": 3, "productivity": 12, "entertainment": 8, "education": 15, "other": 4,
}
CATEGORY_DURATION_SIGMA = {
    "social": 0.6, "productivity": 0.7, "entertainment": 0.8, "education": 0.6, "other": 0.5,
}

PERSONAS = ["healthy", "borderline", "high_risk", "weekend_binger"]
PERSONA_WEIGHTS = [0.35, 0.30, 0.20, 0.15]

# Base hourly mass for "day" hours 5-23 (index 0 = hour 5). Night hours 0-4
# are controlled separately via night_ratio so it matches the 00:00-05:00
# definition used later in feature engineering.
_BASE_DAY_PROFILE_RAW = np.array(
    [0.5, 1, 2, 3, 2.5, 2, 2, 3, 3, 2, 2, 2.5, 3, 4, 5, 5, 4, 3, 2]
)
BASE_DAY_PROFILE = _BASE_DAY_PROFILE_RAW / _BASE_DAY_PROFILE_RAW.sum()
NIGHT_HOURS = np.arange(0, 5)
DAY_ACTIVE_HOURS = np.arange(5, 24)

PERSONA_PARAMS = {
    "healthy": dict(
        lambda_weekday=16, lambda_weekend=18,
        cat_weights=dict(social=0.20, productivity=0.30, entertainment=0.20, education=0.15, other=0.15),
        duration_mult=dict(social=0.8, productivity=2.0, entertainment=0.8, education=1.8, other=1.0),
        night_ratio=0.02,
        unlock_base=1.0,
        drift_kind="occasional_spike",
    ),
    "borderline": dict(
        lambda_weekday=26, lambda_weekend=30,
        cat_weights=dict(social=0.35, productivity=0.20, entertainment=0.25, education=0.10, other=0.10),
        duration_mult=dict(social=1.1, productivity=1.1, entertainment=1.2, education=1.0, other=1.0),
        night_ratio=0.10,
        unlock_base=1.4,
        drift_kind="trending",
    ),
    "high_risk": dict(
        lambda_weekday=42, lambda_weekend=48,
        cat_weights=dict(social=0.45, productivity=0.08, entertainment=0.35, education=0.02, other=0.10),
        duration_mult=dict(social=0.6, productivity=0.6, entertainment=0.7, education=0.5, other=0.7),
        night_ratio=0.30,
        unlock_base=2.2,
        drift_kind="trending",
    ),
    "weekend_binger": dict(
        lambda_weekday=18, lambda_weekend=50,
        cat_weights=dict(social=0.25, productivity=0.30, entertainment=0.20, education=0.15, other=0.10),
        duration_mult=dict(social=1.0, productivity=1.3, entertainment=1.0, education=1.2, other=1.0),
        night_ratio=0.05,
        unlock_base=1.3,
        drift_kind="stable",
    ),
}


@dataclass
class UserParams:
    persona: str
    lambda_weekday: float
    lambda_weekend: float
    cat_weights: dict
    duration_mult: dict
    night_ratio: float
    unlock_base: float
    drift_direction: int = 0          # -1 improving, 0 stable, +1 worsening
    spike_weeks: tuple = field(default_factory=tuple)  # for occasional_spike drift


def _jitter(rng: np.random.Generator, value: float, sigma: float, lo: float, hi: float) -> float:
    return float(np.clip(value * rng.normal(1.0, sigma), lo, hi))


def _jitter_weights(rng: np.random.Generator, weights: dict, sigma: float = 0.15) -> dict:
    keys = list(weights.keys())
    vals = np.array([weights[k] for k in keys])
    noisy = np.clip(vals * rng.normal(1.0, sigma, size=len(vals)), 0.01, None)
    noisy = noisy / noisy.sum()
    return dict(zip(keys, noisy))


def _build_user_params(persona: str, rng: np.random.Generator) -> UserParams:
    base = PERSONA_PARAMS[persona]
    lambda_weekday = _jitter(rng, base["lambda_weekday"], 0.18, 4, 90)
    lambda_weekend = _jitter(rng, base["lambda_weekend"], 0.18, 4, 100)
    cat_weights = _jitter_weights(rng, base["cat_weights"])
    duration_mult = {
        cat: _jitter(rng, mult, 0.15, 0.3, 3.0)
        for cat, mult in base["duration_mult"].items()
    }
    night_ratio = float(np.clip(base["night_ratio"] + rng.normal(0, 0.03), 0.005, 0.65))
    unlock_base = _jitter(rng, base["unlock_base"], 0.2, 0.2, 4.0)

    drift_direction = 0
    spike_weeks: tuple = ()
    kind = base["drift_kind"]
    if kind == "trending" and rng.random() < 0.35:
        # borderline users skew toward worsening, high_risk users skew toward recovering
        skew = 0.7 if persona == "borderline" else 0.3
        drift_direction = 1 if rng.random() < skew else -1
    elif kind == "occasional_spike" and rng.random() < 0.12:
        start_week = int(rng.integers(3, 10))
        spike_weeks = (start_week, start_week + 1)

    return UserParams(
        persona=persona,
        lambda_weekday=lambda_weekday,
        lambda_weekend=lambda_weekend,
        cat_weights=cat_weights,
        duration_mult=duration_mult,
        night_ratio=night_ratio,
        unlock_base=unlock_base,
        drift_direction=drift_direction,
        spike_weeks=spike_weeks,
    )


def _hour_probabilities(night_ratio: float) -> np.ndarray:
    probs = np.zeros(24)
    probs[NIGHT_HOURS] = night_ratio / len(NIGHT_HOURS)
    probs[DAY_ACTIVE_HOURS] = BASE_DAY_PROFILE * (1 - night_ratio)
    probs = probs / probs.sum()
    return probs


def _effective_day_params(params: UserParams, week_idx: int, is_weekend: bool) -> dict:
    lam = params.lambda_weekend if is_weekend else params.lambda_weekday
    night_ratio = params.night_ratio
    duration_mult = dict(params.duration_mult)

    if params.drift_direction != 0:
        severity = float(np.clip(1 + params.drift_direction * 0.028 * week_idx, 0.5, 1.7))
        lam *= severity
        night_ratio = float(np.clip(night_ratio * severity, 0.005, 0.7))
        for cat in ("productivity", "education"):
            duration_mult[cat] = duration_mult[cat] / severity
        for cat in ("social", "entertainment"):
            duration_mult[cat] = duration_mult[cat] * severity

    if params.spike_weeks and week_idx in params.spike_weeks:
        lam *= 1.8
        night_ratio = float(np.clip(night_ratio * 3, 0.01, 0.6))

    return dict(lam=lam, night_ratio=night_ratio, duration_mult=duration_mult)


def _generate_day(
    user_id: str,
    date: dt.date,
    params: UserParams,
    week_idx: int,
    is_weekend: bool,
    rng: np.random.Generator,
) -> Optional[dict]:
    eff = _effective_day_params(params, week_idx, is_weekend)
    n_sessions = rng.poisson(eff["lam"])
    if n_sessions <= 0:
        return None

    hour_probs = _hour_probabilities(eff["night_ratio"])
    hours = rng.choice(24, size=n_sessions, p=hour_probs)
    minutes = rng.integers(0, 60, size=n_sessions)
    seconds = rng.integers(0, 60, size=n_sessions)

    cat_keys = list(eff["duration_mult"].keys())
    cat_probs = np.array([params.cat_weights[c] for c in cat_keys])
    cat_probs = cat_probs / cat_probs.sum()
    categories = rng.choice(cat_keys, size=n_sessions, p=cat_probs)

    medians_sec = np.array([CATEGORY_DURATION_MEDIAN_MIN[c] * 60 for c in categories])
    mults = np.array([eff["duration_mult"][c] for c in categories])
    sigmas = np.array([CATEGORY_DURATION_SIGMA[c] for c in categories])
    log_durations = rng.normal(loc=np.log(medians_sec * mults), scale=sigmas)
    durations_sec = np.clip(np.exp(log_durations), 5, 3 * 3600).astype(int)

    app_names = np.empty(n_sessions, dtype=object)
    for cat in CATEGORIES:
        mask = categories == cat
        cnt = int(mask.sum())
        if cnt:
            app_names[mask] = rng.choice(APP_POOL[cat], size=cnt)

    unlock_counts = rng.poisson(params.unlock_base, size=n_sessions)

    base_ts = pd.Timestamp(date)
    timestamps = (
        base_ts
        + pd.to_timedelta(hours, unit="h")
        + pd.to_timedelta(minutes, unit="m")
        + pd.to_timedelta(seconds, unit="s")
    )
    is_night = hours < 5

    return dict(
        user_id=np.full(n_sessions, user_id, dtype=object),
        timestamp=timestamps,
        app_name=app_names,
        app_category=categories,
        session_duration_sec=durations_sec,
        unlock_count=unlock_counts,
        is_night_usage=is_night,
    )


def generate(
    n_users: int = 500,
    n_weeks: int = 12,
    seed: int = 42,
    missing_day_prob: float = 0.03,
    row_drop_frac: float = 0.03,
    dup_frac: float = 0.005,
    missing_category_frac: float = 0.01,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Generate synthetic usage logs and the hidden persona ground truth.

    Returns (logs_df, users_meta_df). users_meta_df is kept aside for cluster
    evaluation only -- it must never be fed into the models as a feature.
    """
    rng = np.random.default_rng(seed)
    start_date = dt.date(2025, 1, 6)  # a Monday
    n_days = n_weeks * 7

    user_ids = [f"u{idx:04d}" for idx in range(n_users)]
    personas = rng.choice(PERSONAS, size=n_users, p=PERSONA_WEIGHTS)

    users_meta = []
    columns: dict[str, list] = {col: [] for col in SCHEMA_COLUMNS}

    for user_id, persona in zip(user_ids, personas):
        params = _build_user_params(persona, rng)
        users_meta.append(dict(
            user_id=user_id,
            persona=persona,
            drift_direction=params.drift_direction,
            had_spike=bool(params.spike_weeks),
        ))

        for day_offset in range(n_days):
            if rng.random() < missing_day_prob:
                continue
            date = start_date + dt.timedelta(days=day_offset)
            week_idx = day_offset // 7
            is_weekend = date.weekday() >= 5
            day_data = _generate_day(user_id, date, params, week_idx, is_weekend, rng)
            if day_data is None:
                continue
            for col in SCHEMA_COLUMNS:
                columns[col].append(day_data[col])

    logs_df = pd.DataFrame({
        "user_id": np.concatenate(columns["user_id"]),
        "timestamp": pd.concat([pd.Series(t) for t in columns["timestamp"]], ignore_index=True),
        "app_name": np.concatenate(columns["app_name"]),
        "app_category": np.concatenate(columns["app_category"]),
        "session_duration_sec": np.concatenate(columns["session_duration_sec"]),
        "unlock_count": np.concatenate(columns["unlock_count"]),
        "is_night_usage": np.concatenate(columns["is_night_usage"]),
    })

    logs_df = _inject_noise(logs_df, rng, row_drop_frac, dup_frac, missing_category_frac)
    logs_df = logs_df.sort_values(["user_id", "timestamp"]).reset_index(drop=True)

    users_meta_df = pd.DataFrame(users_meta)
    return logs_df, users_meta_df


def _inject_noise(
    df: pd.DataFrame,
    rng: np.random.Generator,
    row_drop_frac: float,
    dup_frac: float,
    missing_category_frac: float,
) -> pd.DataFrame:
    """Simulate real-world logging messiness: dropped rows, duplicate rows,
    and uncategorized apps -- without ever changing the schema."""
    keep_mask = rng.random(len(df)) >= row_drop_frac
    df = df.loc[keep_mask].reset_index(drop=True)

    dup_mask = rng.random(len(df)) < dup_frac
    duplicates = df.loc[dup_mask]
    df = pd.concat([df, duplicates], ignore_index=True)

    cat_missing_mask = rng.random(len(df)) < missing_category_frac
    df.loc[cat_missing_mask, "app_category"] = np.nan

    return df


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--users", type=int, default=500)
    parser.add_argument("--weeks", type=int, default=12)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out-dir", type=str, default=str(Path(__file__).parent))
    args = parser.parse_args()

    logs_df, users_meta_df = generate(n_users=args.users, n_weeks=args.weeks, seed=args.seed)

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    logs_path = out_dir / "usage_logs.csv"
    meta_path = out_dir / "users_meta.csv"
    logs_df.to_csv(logs_path, index=False)
    users_meta_df.to_csv(meta_path, index=False)

    print(f"Wrote {len(logs_df):,} rows to {logs_path}")
    print(f"Wrote {len(users_meta_df):,} user records to {meta_path}")
    print("\nPersona distribution:")
    print(users_meta_df["persona"].value_counts())
    print("\nSample rows:")
    print(logs_df.head(10).to_string())


if __name__ == "__main__":
    main()
