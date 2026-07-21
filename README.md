# Digital Addiction Monitor

Digital Addiction Monitor tracks how a user actually uses their phone and scores "addiction risk"
against their *own* historical baseline instead of a generic screen-time limit. A Spring Boot
backend persists and aggregates usage-session data, and a Python ML service turns that history into
a risk score, a behavioral persona, and personalized suggestions. There is currently no frontend
client in this repo — see "What's novel" below for the product thesis these two services implement.

## Components

| Component   | Path           | Stack                          | Port |
|-------------|----------------|---------------------------------|------|
| ML service  | `ml-service/`  | Python, FastAPI, scikit-learn   | 8000 |
| Backend     | `backend/`     | Java 17, Spring Boot, Maven, H2 | 8080 |

## Prerequisites

- Java 17
- Python 3.11

## Running it

Start the ML service first, then the backend — the backend calls out to the ML service on every
dashboard request.

### 1. ML service (port 8000)

```
cd ml-service
python -m venv venv
venv\Scripts\activate        # macOS/Linux: source venv/bin/activate
pip install -r requirements.txt
python data/generator.py     # generates synthetic usage data under data/
python train.py              # trains the models into models/*.joblib
uvicorn app.main:app --reload --port 8000
```

### 2. Backend (port 8080)

```
cd backend
mvn spring-boot:run
```

The backend expects the ML service at `http://localhost:8000` by default (override with the
`ML_SERVICE_URL` env var). With both running, `POST /api/logs/sync` and `GET /api/dashboard/{userId}`
on `localhost:8080` are ready to take usage-log data from any client.

## What's novel

- **Predictive, not descriptive.** The ML service forecasts where usage is heading, not just a
  tally of what already happened.
- **Attention quality, not minutes.** Focus is scored from session fragmentation and switching
  behavior, not raw screen-time totals.
- **Personal baseline, not fixed limits.** Risk is measured as a deviation from *your* own
  historical patterns, not a one-size-fits-all cutoff.
- **Onset warning.** The system flags early drift toward risky patterns before they become
  established habits, rather than reporting only after the fact.

## Getting the code

```
git clone https://github.com/Vasavi-vecha/digital-addiction-monitor.git
```
