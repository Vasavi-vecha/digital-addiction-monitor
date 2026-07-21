from __future__ import annotations

from tests.conftest import requires_trained_artifacts


def _snapshot(**overrides) -> dict:
    base = {
        "total_screen_min": 300.0,
        "screen_min_social": 100.0,
        "screen_min_productivity": 80.0,
        "screen_min_entertainment": 90.0,
        "screen_min_education": 20.0,
        "screen_min_other": 10.0,
        "night_ratio": 0.1,
        "unlock_count_week": 150.0,
        "mean_session_sec": 300.0,
        "median_session_sec": 200.0,
        "max_session_sec": 1800.0,
        "context_switch_rate": 5.0,
        "n_sessions": 60.0,
        "deep_focus_block_count": 3.0,
        "deep_focus_minutes": 90.0,
        "weekend_weekday_delta_pct": 10.0,
    }
    base.update(overrides)
    return base


@requires_trained_artifacts
def test_predict_risk_happy_path(client):
    payload = {"weekly_history": [_snapshot() for _ in range(4)]}
    response = client.post("/predict/risk", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert 0.0 <= body["predicted_risk_score"] <= 100.0
    assert body["trend_direction"] in ("increasing", "decreasing", "stable")
    assert isinstance(body["contributing_trend_slopes"], dict)


def test_predict_risk_rejects_three_weeks(client):
    payload = {"weekly_history": [_snapshot() for _ in range(3)]}
    response = client.post("/predict/risk", json=payload)
    # WeeklySnapshot's own field_validator raises during pydantic request
    # parsing (before the route body even executes), so FastAPI's default
    # request-validation handling applies here -- 422, not the app's custom
    # ValueError->400 handler (that one only covers business-logic errors
    # raised inside a route function after parsing succeeds).
    assert response.status_code == 422


def test_predict_risk_rejects_five_weeks(client):
    payload = {"weekly_history": [_snapshot() for _ in range(5)]}
    response = client.post("/predict/risk", json=payload)
    assert response.status_code == 422


def test_predict_risk_rejects_zero_weeks(client):
    payload = {"weekly_history": []}
    response = client.post("/predict/risk", json=payload)
    assert response.status_code == 422
