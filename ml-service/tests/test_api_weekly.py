from __future__ import annotations

from data.generator import generate
from tests.conftest import requires_trained_artifacts


def _clean_logs_payload(n_weeks: int = 6, seed: int = 99):
    # noise knobs zeroed out so every row cleanly matches UsageLogRow's schema
    # (missing_category_frac in particular can otherwise inject NaN
    # app_category values that aren't valid under the Literal/None field).
    logs_df, meta_df = generate(
        n_users=1, n_weeks=n_weeks, seed=seed,
        missing_day_prob=0.0, row_drop_frac=0.0, dup_frac=0.0, missing_category_frac=0.0,
    )
    user_id = meta_df["user_id"].iloc[0]
    logs = [
        {
            "user_id": row.user_id,
            "timestamp": row.timestamp.isoformat(),
            "app_name": row.app_name,
            "app_category": row.app_category,
            "session_duration_sec": float(row.session_duration_sec),
            "unlock_count": int(row.unlock_count),
            "is_night_usage": bool(row.is_night_usage),
        }
        for row in logs_df.itertuples(index=False)
    ]
    return user_id, logs


@requires_trained_artifacts
def test_analyze_weekly_happy_path(client):
    user_id, logs = _clean_logs_payload()
    response = client.post("/analyze/weekly", json={"user_id": user_id, "logs": logs})
    assert response.status_code == 200
    body = response.json()
    assert body["user_id"] == user_id
    assert isinstance(body["week_index"], int)
    assert 0.0 <= body["risk_score"] <= 100.0
    assert isinstance(body["cluster_persona"], str) and body["cluster_persona"]
    assert isinstance(body["top_drivers"], list)
    assert isinstance(body["week_over_week_deltas"], list)


def test_analyze_weekly_rejects_empty_logs(client):
    response = client.post("/analyze/weekly", json={"user_id": "u0000", "logs": []})
    # raised as a plain ValueError inside weekly_service.analyze_weekly
    # (after successful request parsing), so the app's custom
    # ValueError->400 handler applies here.
    assert response.status_code == 400


def test_analyze_weekly_rejects_user_id_with_no_matching_rows(client):
    _, logs = _clean_logs_payload(n_weeks=2, seed=7)
    response = client.post("/analyze/weekly", json={"user_id": "does-not-exist", "logs": logs})
    assert response.status_code == 400
