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

### 3. Android app (WiFi, no cable needed once installed)

The app talks to the backend over plain HTTP, so the simplest way to demo it is to have the phone
and the laptop running ml-service + backend on the same WiFi network — no cloud hosting, no
ongoing USB connection.

1. **Find your laptop's WiFi IP address.**
   - Windows: run `ipconfig`, look for "Wireless LAN adapter Wi-Fi" → IPv4 Address (e.g. `192.168.1.23`).
   - macOS/Linux: `ipconfig getifaddr en0` (or `ifconfig`).
2. **Put that IP into the app.** Open
   `android-app/app/src/main/java/com/example/digitaladdictionmonitor/network/RetrofitClient.kt`
   and replace the placeholder in `BASE_URL` with your real IP:
   ```
   private const val BASE_URL = "http://192.168.1.23:8080/"
   ```
   This address can change if the laptop reconnects to WiFi (unless the router assigns a static
   IP), so re-check it if the app suddenly can't reach the backend.
3. Open the `android-app` folder in Android Studio and let Gradle sync.
4. Make sure the phone is on the **same WiFi network** as the laptop.
5. Connect the phone over USB **just once**, to install the app (enable Developer Options + USB
   Debugging on the phone first: Settings → About phone → tap "Build number" 7 times → Developer
   options → USB debugging).
6. Run the app from Android Studio (green ▶ button) with the phone selected as target.
7. Once installed and open, **the USB cable can be unplugged** — the app reaches the backend over
   WiFi using the IP from step 2, not the cable.

If Windows Firewall prompts to allow the Java process on port 8080, click **Allow**, or the phone's
requests will be silently blocked.

**Alternative (USB tether instead of WiFi):** connect over USB and run
`adb reverse tcp:8080 tcp:8080` (needs re-running each debug session), with `BASE_URL` set to
`http://localhost:8080/` instead. An emulator can be used instead of a physical device, but needs
enough RAM to boot reliably — if it gets stuck at `offline` in `adb devices`, use a physical device.

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
