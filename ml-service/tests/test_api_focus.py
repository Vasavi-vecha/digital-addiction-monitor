from __future__ import annotations

from tests.conftest import requires_trained_artifacts


@requires_trained_artifacts
def test_predict_focus_happy_path(client):
    payload = {
        "app_category": "social",
        "session_duration_sec": 45,
        "hour": 23,
        "is_weekend": False,
        "is_night_usage": True,
        "unlock_count": 3,
        "gap_before_sec": 20,
        "prev_app_category": "entertainment",
        "recent_switch_count_15min": 5,
    }
    response = client.post("/predict/focus", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["label"] in ("focused", "distracted", "passive")
    assert 0.0 <= body["confidence"] <= 1.0
    assert sum(body["probabilities"].values()) == 1.0 or abs(sum(body["probabilities"].values()) - 1.0) < 1e-6


@requires_trained_artifacts
def test_predict_focus_uses_defaults_for_optional_fields(client):
    payload = {
        "app_category": "productivity",
        "session_duration_sec": 1800,
        "hour": 10,
        "is_weekend": False,
        "is_night_usage": False,
    }
    response = client.post("/predict/focus", json=payload)
    assert response.status_code == 200


def test_predict_focus_rejects_out_of_range_hour(client):
    payload = {
        "app_category": "social",
        "session_duration_sec": 45,
        "hour": 24,
        "is_weekend": False,
        "is_night_usage": False,
    }
    response = client.post("/predict/focus", json=payload)
    assert response.status_code == 422


def test_predict_focus_rejects_unknown_category(client):
    payload = {
        "app_category": "gaming",
        "session_duration_sec": 45,
        "hour": 10,
        "is_weekend": False,
        "is_night_usage": False,
    }
    response = client.post("/predict/focus", json=payload)
    assert response.status_code == 422
