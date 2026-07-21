from __future__ import annotations

from tests.conftest import requires_trained_artifacts


@requires_trained_artifacts
def test_health_ok(client):
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert set(body["models_loaded"]) == {
        "focus_classifier", "risk_regressor", "behavior_kmeans", "behavior_scaler",
    }
