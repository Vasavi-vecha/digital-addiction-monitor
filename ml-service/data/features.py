"""Feature engineering: raw usage-session logs -> per-user, per-week feature table.

Input is any CSV matching generator.SCHEMA_COLUMNS -- this module never assumes
the rows came from the synthetic generator, so a real Android UsageStatsManager
export can replace it without touching this code.

Every weekly metric is also expressed as a personal-baseline delta (percent
change and z-score against that same user's own trailing 4-week history).
That is the core design choice behind the whole project: a user is compared
to their own past, not to a fixed external threshold.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

CATEGORIES = ["social", "productivity", "entertainment", "education", "other"]

DEEP_FOCUS_MIN_BLOCK_SEC = 25 * 60   # >= 25 continuous minutes counts as deep focus
DEEP_FOCUS_MAX_GAP_SEC = 120         # allow up to a 2-minute gap and still call it "continuous"
BASELINE_WINDOW_WEEKS = 4

# Metrics that get a personal-baseline % change and z-score. This list is the
# "first-class" feature set the project's thesis depends on.
BASELINE_METRIC_COLUMNS = [
    "total_screen_min",
    "screen_min_social",
    "screen_min_productivity",
    "screen_min_entertainment",
    "screen_min_education",
    "screen_min_other",
    "night_ratio",
    "unlock_count_week",
    "mean_session_sec",
    "median_session_sec",
    "max_session_sec",
    "context_switch_rate",
    "n_sessions",
    "deep_focus_block_count",
    "deep_focus_minutes",
    "weekend_weekday_delta_pct",
]


def prepare_logs(logs_df: pd.DataFrame) -> pd.DataFrame:
    """Parse and clean a raw usage-log dataframe. Idempotent, side-effect free."""
    df = logs_df.copy()
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    df["app_category"] = df["app_category"].fillna("other")
    unknown_cats = ~df["app_category"].isin(CATEGORIES)
    df.loc[unknown_cats, "app_category"] = "other"
    df["session_duration_sec"] = df["session_duration_sec"].clip(lower=0)
    df["unlock_count"] = df["unlock_count"].fillna(0).clip(lower=0)
    df["is_night_usage"] = df["is_night_usage"].astype(bool)
    df = df.sort_values(["user_id", "timestamp"]).reset_index(drop=True)
    return df


def week_reference(df: pd.DataFrame) -> pd.Timestamp:
    """Monday on/before the earliest timestamp in the data -- week 0 anchor.

    Derived from the data itself (not a hardcoded date) so this works
    regardless of when the logs were actually recorded.
    """
    earliest = df["timestamp"].min().normalize()
    return earliest - pd.Timedelta(days=earliest.weekday())


def assign_week_index(df: pd.DataFrame, ref: pd.Timestamp) -> pd.Series:
    return ((df["timestamp"] - ref).dt.days // 7).astype(int)


def compute_deep_focus_blocks(df: pd.DataFrame) -> pd.DataFrame:
    """Merge consecutive same-app productivity sessions (small gaps allowed)
    into continuous blocks, keeping only blocks >= 25 minutes.

    Vectorized: a new block starts whenever the app changes, the category
    isn't productivity, or the gap since the previous session's end exceeds
    DEEP_FOCUS_MAX_GAP_SEC.
    """
    tagged = tag_deep_focus_blocks(df)
    prod = tagged[tagged["is_prod"]]
    blocks = prod.groupby(["user_id", "block_id"]).agg(
        duration_sec=("session_duration_sec", "sum"),
        start=("timestamp", "min"),
        app_name=("app_name", "first"),
    ).reset_index()

    return blocks[blocks["duration_sec"] >= DEEP_FOCUS_MIN_BLOCK_SEC].drop(columns="block_id")


def tag_deep_focus_blocks(df: pd.DataFrame) -> pd.DataFrame:
    """Session-level view: tag each row with its block_id and whether it
    belongs to a qualifying (>= 25 min) deep-focus block. Used both by the
    weekly aggregation above and by the per-session focus-classification
    features in train.py.
    """
    d = df.copy()
    d["session_end"] = d["timestamp"] + pd.to_timedelta(d["session_duration_sec"], unit="s")
    d["is_prod"] = d["app_category"] == "productivity"

    same_user = d["user_id"] == d["user_id"].shift(1)
    same_app = d["app_name"] == d["app_name"].shift(1)
    prev_is_prod = d["is_prod"].shift(1).eq(True)
    gap_sec = (d["timestamp"] - d["session_end"].shift(1)).dt.total_seconds()
    continues_chain = same_user & same_app & prev_is_prod & d["is_prod"] & (gap_sec <= DEEP_FOCUS_MAX_GAP_SEC)

    d["block_id"] = (~continues_chain).groupby(d["user_id"]).cumsum()

    block_totals = d.loc[d["is_prod"]].groupby(["user_id", "block_id"])["session_duration_sec"].transform("sum")
    d["block_duration_sec"] = 0.0
    d.loc[d["is_prod"], "block_duration_sec"] = block_totals
    d["is_deep_focus_block"] = d["is_prod"] & (d["block_duration_sec"] >= DEEP_FOCUS_MIN_BLOCK_SEC)
    return d


def build_base_weekly(df: pd.DataFrame, ref: pd.Timestamp) -> pd.DataFrame:
    """Per (user_id, week): screen time totals, category splits, night ratio,
    unlock frequency, session-length stats, and context-switch rate."""
    d = df.copy()
    d["week"] = assign_week_index(d, ref)
    d["night_duration_sec"] = np.where(d["is_night_usage"], d["session_duration_sec"], 0)

    prev_user = d["user_id"] == d["user_id"].shift(1)
    prev_app = d["app_name"].shift(1)
    d["is_switch"] = prev_user & (d["app_name"] != prev_app)

    base = d.groupby(["user_id", "week"]).agg(
        total_screen_sec=("session_duration_sec", "sum"),
        night_sec=("night_duration_sec", "sum"),
        unlock_count_week=("unlock_count", "sum"),
        mean_session_sec=("session_duration_sec", "mean"),
        median_session_sec=("session_duration_sec", "median"),
        max_session_sec=("session_duration_sec", "max"),
        n_sessions=("session_duration_sec", "count"),
        n_switches=("is_switch", "sum"),
    ).reset_index()

    base["total_screen_min"] = base["total_screen_sec"] / 60
    base["night_ratio"] = (base["night_sec"] / base["total_screen_sec"].replace(0, np.nan)).fillna(0)
    total_hours = (base["total_screen_sec"] / 3600).replace(0, np.nan)
    base["context_switch_rate"] = (base["n_switches"] / total_hours).fillna(0)

    cat_pivot = (
        d.groupby(["user_id", "week", "app_category"])["session_duration_sec"]
        .sum().unstack(fill_value=0) / 60
    )
    cat_pivot = cat_pivot.reindex(columns=CATEGORIES, fill_value=0)
    cat_pivot.columns = [f"screen_min_{c}" for c in cat_pivot.columns]
    cat_pivot = cat_pivot.reset_index()

    weekly = base.merge(cat_pivot, on=["user_id", "week"], how="left")
    return weekly.drop(columns=["total_screen_sec", "night_sec", "n_switches"])


def add_deep_focus_features(weekly: pd.DataFrame, df: pd.DataFrame, ref: pd.Timestamp) -> pd.DataFrame:
    blocks = compute_deep_focus_blocks(df)
    if blocks.empty:
        weekly["deep_focus_block_count"] = 0
        weekly["deep_focus_minutes"] = 0.0
        return weekly

    blocks = blocks.copy()
    blocks["week"] = assign_week_index(blocks.rename(columns={"start": "timestamp"}), ref)
    focus_weekly = blocks.groupby(["user_id", "week"]).agg(
        deep_focus_block_count=("duration_sec", "count"),
        deep_focus_minutes=("duration_sec", lambda s: s.sum() / 60),
    ).reset_index()

    weekly = weekly.merge(focus_weekly, on=["user_id", "week"], how="left")
    weekly[["deep_focus_block_count", "deep_focus_minutes"]] = (
        weekly[["deep_focus_block_count", "deep_focus_minutes"]].fillna(0)
    )
    return weekly


def add_weekday_weekend_delta(weekly: pd.DataFrame, df: pd.DataFrame, ref: pd.Timestamp) -> pd.DataFrame:
    """% difference between average daily screen time on weekends vs weekdays.
    This is the signal that catches a weekend-binger persona that looks
    ordinary on any single weekday."""
    d = df.copy()
    d["week"] = assign_week_index(d, ref)
    d["date"] = d["timestamp"].dt.date
    d["is_weekend"] = d["timestamp"].dt.weekday >= 5

    daily = d.groupby(["user_id", "week", "date", "is_weekend"])["session_duration_sec"].sum().reset_index()
    daily["minutes"] = daily["session_duration_sec"] / 60

    day_avg = daily.groupby(["user_id", "week", "is_weekend"])["minutes"].mean().unstack("is_weekend")
    day_avg = day_avg.rename(columns={False: "weekday_avg_min", True: "weekend_avg_min"}).reset_index()
    for col in ("weekday_avg_min", "weekend_avg_min"):
        if col not in day_avg.columns:
            day_avg[col] = 0.0
    day_avg = day_avg.fillna(0.0)

    day_avg["weekend_weekday_delta_pct"] = (
        (day_avg["weekend_avg_min"] - day_avg["weekday_avg_min"])
        / day_avg["weekday_avg_min"].replace(0, np.nan) * 100
    ).fillna(0.0)

    weekly = weekly.merge(
        day_avg[["user_id", "week", "weekend_weekday_delta_pct"]], on=["user_id", "week"], how="left"
    )
    weekly["weekend_weekday_delta_pct"] = weekly["weekend_weekday_delta_pct"].fillna(0.0)
    return weekly


def add_personal_baseline_features(
    weekly: pd.DataFrame, metric_cols: list[str] = BASELINE_METRIC_COLUMNS, window: int = BASELINE_WINDOW_WEEKS
) -> pd.DataFrame:
    """For every metric, add trailing-N-week baseline mean/std plus this
    week's % change and z-score against that personal baseline.

    Uses only *prior* weeks (shift(1) before rolling) so this can never leak
    the current week's own value into its own baseline.
    """
    weekly = weekly.sort_values(["user_id", "week"]).reset_index(drop=True)
    eps = 1e-6

    for col in metric_cols:
        grouped = weekly.groupby("user_id")[col]
        trailing_mean = grouped.transform(lambda s: s.shift(1).rolling(window, min_periods=1).mean())
        # std needs >= 2 prior points to be defined; with only 1 it must stay
        # NaN rather than be treated as 0 (which would blow up the z-score).
        trailing_std = grouped.transform(lambda s: s.shift(1).rolling(window, min_periods=2).std())

        weekly[f"{col}_baseline_mean"] = trailing_mean
        weekly[f"{col}_baseline_std"] = trailing_std
        weekly[f"{col}_pct_change"] = (
            (weekly[col] - trailing_mean) / (trailing_mean.abs() + eps) * 100
        )
        weekly[f"{col}_zscore"] = (weekly[col] - trailing_mean) / (trailing_std + eps)

    return weekly


def build_weekly_features(logs_df: pd.DataFrame) -> pd.DataFrame:
    """Full pipeline: raw session logs -> per-user, per-week feature table
    with personal-baseline deltas. This is the table fed into every model."""
    df = prepare_logs(logs_df)
    ref = week_reference(df)

    weekly = build_base_weekly(df, ref)
    weekly = add_deep_focus_features(weekly, df, ref)
    weekly = add_weekday_weekend_delta(weekly, df, ref)
    weekly = add_personal_baseline_features(weekly)

    return weekly.sort_values(["user_id", "week"]).reset_index(drop=True)


def main() -> None:
    here = Path(__file__).parent
    logs_df = pd.read_csv(here / "usage_logs.csv")
    weekly = build_weekly_features(logs_df)
    out_path = here / "weekly_features.csv"
    weekly.to_csv(out_path, index=False)
    print(f"Wrote {len(weekly):,} user-week rows, {weekly.shape[1]} columns to {out_path}")
    print(f"\nColumns:\n{list(weekly.columns)}")
    print("\nSample rows (first user, all weeks):")
    first_user = weekly["user_id"].iloc[0]
    cols_preview = [
        "user_id", "week", "total_screen_min", "night_ratio", "context_switch_rate",
        "deep_focus_block_count", "deep_focus_minutes", "weekend_weekday_delta_pct",
        "total_screen_min_pct_change", "total_screen_min_zscore",
    ]
    print(weekly.loc[weekly["user_id"] == first_user, cols_preview].to_string(index=False))


if __name__ == "__main__":
    main()
