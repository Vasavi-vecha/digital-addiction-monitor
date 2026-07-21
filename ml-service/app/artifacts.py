"""Loads every trained model/artifact once at process startup.

FastAPI endpoints call get_artifacts() to reach these singletons instead of
re-reading joblib files per-request.
"""

from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import joblib
import pandas as pd

MODELS_DIR = Path(__file__).parent.parent / "models"


@dataclass
class Artifacts:
    focus_classifier: object
    focus_feature_cols: list[str]
    risk_regressor: object
    risk_feature_cols: list[str]
    risk_score_bounds: dict
    behavior_kmeans: object
    behavior_scaler: object
    cluster_persona_map: dict[int, str]


@lru_cache(maxsize=1)
def get_artifacts() -> Artifacts:
    focus_classifier = joblib.load(MODELS_DIR / "focus_classifier.joblib")
    risk_regressor = joblib.load(MODELS_DIR / "risk_regressor.joblib")
    risk_score_bounds = joblib.load(MODELS_DIR / "risk_score_bounds.joblib")
    behavior_kmeans = joblib.load(MODELS_DIR / "behavior_kmeans.joblib")
    behavior_scaler = joblib.load(MODELS_DIR / "behavior_scaler.joblib")

    centroids = pd.read_csv(MODELS_DIR / "cluster_centroids.csv")
    cluster_persona_map = dict(zip(centroids["cluster"], centroids["persona_label"]))

    return Artifacts(
        focus_classifier=focus_classifier,
        focus_feature_cols=list(focus_classifier.feature_names_in_),
        risk_regressor=risk_regressor,
        risk_feature_cols=list(risk_regressor.feature_names_in_),
        risk_score_bounds=risk_score_bounds,
        behavior_kmeans=behavior_kmeans,
        behavior_scaler=behavior_scaler,
        cluster_persona_map=cluster_persona_map,
    )
