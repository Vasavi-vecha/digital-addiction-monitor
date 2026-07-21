# ML Service Model Report

This documents what each of the ml-service's four endpoints is backed by, how it was evaluated,
and two decisions that were made deliberately rather than left unfinished: the risk regressor's
R² ceiling, and the onset endpoint's rule-based (not trained) design.

All numbers below come from `ml-service/models/training_summary.json`, produced by `train.py`
against synthetic data from `data/generator.py` (500 users × 12 weeks, time-based train/test split
at week 8/9 to prevent leakage).

## Core thesis

Every model in this service scores a user against their **own trailing 4-week history**, not a
fixed population threshold. That's a product decision, not just a technical default — see
`scoring.py`'s module docstring and `data/features.py`'s `add_personal_baseline_features`, which
strictly uses `shift(1)` before rolling so a week can never leak into its own baseline.

## `POST /predict/focus` — RandomForest classifier

Predicts whether a session is `focused` / `distracted` / `passive` from session-level features
(duration, hour, category, recent switch count, gap since previous session).

- Candidates: `RandomForestClassifier` (n_estimators=100, max_depth=12, class_weight="balanced")
  vs. `HistGradientBoostingClassifier`.
- Winner: **RandomForest, 93.31% test accuracy** (GradientBoosting: 93.20% — close, but RF won).
- Labels are weak-supervision heuristics with 10% injected noise (`train.py`'s
  `_inject_label_noise`), not hand-labeled ground truth — accuracy should be read as "recovers the
  heuristic well," not "matches human judgment."

## `POST /predict/risk` — Ridge regression, and why R² stops at 0.71

Forecasts next week's 0–100 risk score from 4 prior weeks of history (83 engineered features:
16 metrics × baseline mean/std, 5 trend slopes, 10 lag terms, 36 polynomial/interaction terms).

**Guide's requirement:** the shipped model must be a plain linear model (`train.py`, `run_risk_regression`:
the winner is selected only from `{LinearRegression, Ridge}` — `RandomForestRegressor` and
`HistGradientBoostingRegressor` are computed purely as a non-linear reference point and are
structurally excluded from ever being shipped).

| Model | RMSE | R² |
|---|---|---|
| LinearRegression | 7.09 | 0.703 |
| **Ridge (shipped)** | **7.00** | **0.711** |
| RandomForestRegressor (reference only) | 7.17 | 0.697 |
| GradientBoosting (reference only) | 7.60 | 0.659 |

The target was R² ≥ 0.93. Two things were checked before concluding that isn't honestly reachable
with a linear model on this target:

1. **Mathematical ceiling.** `compute_risk_score` clips and passes personal z-scores through a
   sigmoid — both non-linear operations. Feeding the model the *current week's own true values*
   directly (real leakage, used only to probe the ceiling, never shipped) still caps a linear fit
   at R² ≈ 0.926. The non-linearity in the scoring formula itself is the bottleneck, not missing
   features.
2. **Some variance is a deliberately unpredictable event.** Rows without a generator-injected
   persona spike have ~1.6× lower risk-score variance than rows with one. `UserParams.spike_weeks`
   injects a random 2-week spike with no footprint in prior weeks — by construction, no model
   (linear or not) can predict it from history.

Note that even the two non-linear reference models (RF, GradientBoosting) scored *worse* than
Ridge with default hyperparameters on this exact feature set — so this isn't a case of "a better
model family was available and skipped," either.

**Conclusion:** 0.71 is treated as the honest ceiling for a linear model here, not a gap. If the
target R² is still required, the real lever is relaxing the linear-model constraint (a tuned
non-linear model) — a policy decision for the guide, not a modeling one.

## `POST /analyze/weekly` — KMeans persona clustering (k=4)

Clusters users into a behavioral persona (`healthy` / `healthy_2` / `borderline` / `high_risk`)
from 16 weekly features, standardized then k-means'd.

- Operating point: **k=4** (product-driven — matches the number of personas the generator injects
  as ground truth), even though silhouette score peaks at k=2.
- Adjusted Rand Index vs. the generator's hidden persona labels: **0.645** — clusters recover the
  injected persona structure reasonably well, not perfectly (expected, since real behavioral
  drift is continuous, not 4 discrete boxes).

## `POST /predict/onset` — rule-based heuristic, not a trained model

This is deliberate, not an oversight. A real onset classifier needs labeled binge-onset events —
moments where a human confirmed "this is where a binge started" — which don't exist without an
actual user study. Training one now would mean fabricating synthetic ground-truth labels for the
one endpoint whose entire point is predicting a real behavioral event, which would undermine the
same honesty standard applied to the risk regressor above.

Instead it's a transparent, documented, weighted combination of the same fragmentation signals the
other two models found meaningful: app-switch rate (35%), social/entertainment share (30%),
short-session share (20%), and unlock rate (15%) over the trailing 30 minutes. See
`app/onset_service.py`'s module docstring: the request/response contract is designed so a trained
classifier is a drop-in swap later, if real onset labels ever become available.

## Test coverage

All of the above (the scoring formula, the onset heuristic, and the feature pipeline) has unit
test coverage under `ml-service/tests/`, plus API-level tests for all four endpoints via FastAPI's
`TestClient`. Run with:

```
cd ml-service
python data/generator.py && python train.py   # only needed once, to produce models/*.joblib
pytest -v
```
