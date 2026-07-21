"""Digital Addiction Monitor -- ML service.

Run: uvicorn app.main:app --reload --port 8000
Docs: http://localhost:8000/docs
"""

from __future__ import annotations

from fastapi import Depends, FastAPI, HTTPException
from fastapi.requests import Request
from fastapi.responses import JSONResponse

from app.artifacts import Artifacts, get_artifacts
from app.focus_service import predict_focus
from app.onset_service import estimate_onset
from app.risk_service import predict_risk
from app.schemas import (
    AnalyzeWeeklyRequest,
    AnalyzeWeeklyResponse,
    FocusPredictionResponse,
    HealthResponse,
    OnsetPredictionRequest,
    OnsetPredictionResponse,
    RiskPredictionRequest,
    RiskPredictionResponse,
    SessionWindowRequest,
)
from app.weekly_service import analyze_weekly

app = FastAPI(
    title="Digital Addiction Monitor -- ML Service",
    description="Predictive, fragmentation-aware, personal-baseline digital wellbeing models.",
    version="1.0.0",
)


@app.exception_handler(ValueError)
async def value_error_handler(request: Request, exc: ValueError) -> JSONResponse:
    return JSONResponse(status_code=400, content={"detail": str(exc)})


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    try:
        artifacts = get_artifacts()
    except FileNotFoundError as exc:
        raise HTTPException(status_code=503, detail=f"Model artifacts not available: {exc}") from exc

    return HealthResponse(
        status="ok",
        models_loaded=["focus_classifier", "risk_regressor", "behavior_kmeans", "behavior_scaler"],
    )


@app.post("/predict/focus", response_model=FocusPredictionResponse)
def predict_focus_endpoint(
    req: SessionWindowRequest, artifacts: Artifacts = Depends(get_artifacts)
) -> FocusPredictionResponse:
    return predict_focus(req, artifacts)


@app.post("/predict/risk", response_model=RiskPredictionResponse)
def predict_risk_endpoint(
    req: RiskPredictionRequest, artifacts: Artifacts = Depends(get_artifacts)
) -> RiskPredictionResponse:
    return predict_risk(req, artifacts)


@app.post("/predict/onset", response_model=OnsetPredictionResponse)
def predict_onset_endpoint(req: OnsetPredictionRequest) -> OnsetPredictionResponse:
    return estimate_onset(req)


@app.post("/analyze/weekly", response_model=AnalyzeWeeklyResponse)
def analyze_weekly_endpoint(
    req: AnalyzeWeeklyRequest, artifacts: Artifacts = Depends(get_artifacts)
) -> AnalyzeWeeklyResponse:
    return analyze_weekly(req, artifacts)
