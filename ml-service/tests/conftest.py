"""Shared pytest fixtures for the ml-service test suite."""

from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.main import app

MODELS_DIR = Path(__file__).parent.parent / "models"
REQUIRED_ARTIFACTS = [
    "focus_classifier.joblib",
    "risk_regressor.joblib",
    "risk_score_bounds.joblib",
    "behavior_kmeans.joblib",
    "behavior_scaler.joblib",
    "cluster_centroids.csv",
]


def has_trained_artifacts() -> bool:
    return all((MODELS_DIR / name).exists() for name in REQUIRED_ARTIFACTS)


requires_trained_artifacts = pytest.mark.skipif(
    not has_trained_artifacts(),
    reason="models/*.joblib not found -- run `python data/generator.py && python train.py` first",
)


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)
