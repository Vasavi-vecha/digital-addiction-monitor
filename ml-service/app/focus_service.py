"""POST /predict/focus: classify a single completed session window.

Builds the exact same feature encoding used in train.py's
_session_feature_matrix, from fields the caller supplies directly (the
service has no access to the user's session history, so gap/switch context
must be passed in already computed by the caller).
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from app.artifacts import Artifacts
from app.schemas import FocusPredictionResponse, SessionWindowRequest
from data.features import CATEGORIES

GAP_CAP_SEC = 3600


def _feature_row(req: SessionWindowRequest) -> pd.DataFrame:
    gap_before = req.gap_before_sec if req.gap_before_sec is not None else GAP_CAP_SEC
    gap_before = max(gap_before, 0)

    row = {
        "session_duration_sec_log": np.log1p(req.session_duration_sec),
        "hour": req.hour,
        "is_weekend": int(req.is_weekend),
        "is_night_usage": int(req.is_night_usage),
        "unlock_count": req.unlock_count,
        "gap_before_sec_log": np.log1p(gap_before),
        "recent_switch_count_15min": req.recent_switch_count_15min,
    }
    for cat in CATEGORIES:
        row[f"cat_{cat}"] = int(req.app_category == cat)
    for cat in CATEGORIES + ["none"]:
        row[f"prev_cat_{cat}"] = int(req.prev_app_category == cat)

    return pd.DataFrame([row])


def predict_focus(req: SessionWindowRequest, artifacts: Artifacts) -> FocusPredictionResponse:
    X = _feature_row(req).reindex(columns=artifacts.focus_feature_cols, fill_value=0)
    model = artifacts.focus_classifier

    proba = model.predict_proba(X)[0]
    probabilities = dict(zip(model.classes_, proba.tolist()))
    label = model.classes_[int(np.argmax(proba))]

    return FocusPredictionResponse(
        label=label,
        confidence=float(np.max(proba)),
        probabilities=probabilities,
    )
