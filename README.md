# Digital Addiction Monitor

Digital Addiction Monitor tracks how you actually use your phone and scores "addiction risk" against
your *own* historical baseline instead of a generic screen-time limit. An Android client collects
real per-app usage sessions, a Spring Boot backend persists and aggregates them, and a Python ML
service turns that history into a risk score, a behavioral persona, and personalized suggestions
that a native dashboard renders on-device.

## Components

| Component   | Path           | Stack                          | Port |
|-------------|----------------|---------------------------------|------|
| ML service  | `ml-service/`  | Python, FastAPI, scikit-learn   | 8000 |
| Backend     | `backend/`     | Java 17, Spring Boot, Maven, H2 | 8080 |
| Android app | `android-app/` | Kotlin, Jetpack Compose         | —    |

## Prerequisites

- Java 17
- Python 3.11
- Android Studio (with an Android SDK and a physical device or emulator)

## Running it

Start the three components in order: ML service, then backend, then the app.

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
`ML_SERVICE_URL` env var).

### 3. Android app

```
cd android-app
```

Open the folder in Android Studio, connect a physical device over USB with debugging enabled, and
run the app. Before the app can reach the backend, forward the device's localhost to your machine:

```
adb reverse tcp:8080 tcp:8080
```

This needs to be re-run each new debug session — it doesn't persist across reboots or reconnects.
An emulator can be used instead of a physical device, but requires the emulator to have enough RAM
to boot reliably; if it gets stuck at `offline` in `adb devices`, use a physical device instead.

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
