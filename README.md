# SafePath AI — Production-Grade Personal Safety Platform

> **SafePath AI** is an end-to-end personal safety platform engineered with an offline-first native Android client (100% Java + XML), a Python/FastAPI backend, an automated historical crime data ETL pipeline, an area-risk machine learning calibration engine, on-device voice emergency keyword spotting, and a RAG-powered safety assistant.

---

## 1. Project Objectives & Core Mission
SafePath AI addresses critical vulnerabilities in modern personal safety tools:
* **Zero False Alarms to Authorities:** Emergency alerts are dispatched strictly and exclusively to verified personal emergency contacts chosen by the user. Strangers and police are never contacted automatically.
* **100% On-Device Voice Privacy:** Spoken codeword monitoring runs entirely on-device via local acoustic modeling (`EXTRA_PREFER_OFFLINE`). No audio is ever recorded or uploaded.
* **Offline-First Resilience:** If an emergency occurs with weak or unavailable cellular coverage, SOS events and journey telemetry are safely queued in a local encrypted Room database and automatically synchronized upon reconnection.
* **Ethical Area-Risk Estimation:** Historical spatial crime density is computed via calibrated statistical models with clear disclaimers, identifying relative historical activity without making individual victimization predictions.

---

## 2. Main Features
1. **One-Tap & Voice SOS Emergency Alerts:** Instant manual SOS trigger with a 5-second abort countdown, or continuous hands-free background emergency voice codeword activation.
2. **Verified Emergency Contact Circle:** Full contact management with mandatory 6-digit OTP verification before any contact can receive emergency broadcast alerts.
3. **Continuous GPS Journey Tracking:** Foreground service with real-time waypoint persistence, speed and bearing telemetry, and explicit accuracy radius disclosure.
4. **Interactive OpenStreetMap with MapLibre:** Open-source vector mapping displaying safe routes, active journey polylines, and real-time historical area risk badges.
5. **Historical Spatial Area-Risk Engine:** Uber H3 hexagonal spatial indexing classifying areas into Low, Moderate, High, or Insufficient Data with contextual safety guidance.
6. **RAG-Powered Safety Assistant:** AI conversational assistant providing safety precautions and app usage guidance, strictly decoupled from the emergency trigger pipeline.

---

## 3. Technology Stack
* **Android Application:** 100% Java (JDK 17), XML Layouts, Android Jetpack (MVVM, Room DB, LiveData, ViewModel, Navigation Component), Retrofit 2.9, OkHttp 4.12, MapLibre SDK, Google Play Services Location.
* **Backend API:** Python 3.11+, FastAPI, Pydantic v2, SQLAlchemy 2.0, Uvicorn, SQLite (local development) / PostgreSQL 15 (staging/production).
* **Data Processing & ML:** Uber H3 (spatial indexing), Pandas, NumPy, Scikit-Learn, PyArrow/Parquet.
* **Security & Auth:** EncryptedSharedPreferences (AES-256), Passlib (Bcrypt), JWT (HS256).

---

## 4. System Architecture & Directory Structure

```text
SafePath-AI/
├── .github/                       ← Issue templates, PR template, CI workflow
│   ├── ISSUE_TEMPLATE/            ← Bug report & feature request templates
│   ├── PULL_REQUEST_TEMPLATE.md   ← Standard pull request review checklist
│   └── workflows/ci.yml           ← Automated test pipeline
├── android-app/                   ← Native Android Application (Java + XML only)
│   ├── app/src/main/
│   │   ├── java/com/safepath/ai/  ← Activities, Fragments, ViewModels, Repositories, DAOs, Services
│   │   └── res/                   ← XML Layouts, Drawables, Values, Navigation Graph
│   ├── app/src/debug/res/xml/     ← Debug network security config (cleartext HTTP enabled)
│   ├── gradle/wrapper/            ← Gradle 8.4 Wrapper
│   ├── build.gradle               ← AGP 8.2.2 build script
│   └── gradle.properties.example  ← Sample developer connection settings
├── backend/                       ← FastAPI REST API & Database
│   ├── app/
│   │   ├── api/v1/endpoints/      ← auth, contacts, journey, risk, sos, assistant, health
│   │   ├── models/                ← SQLAlchemy ORM database models
│   │   ├── schemas/               ← Pydantic request/response contracts
│   │   └── security/              ← Password hashing, JWT token handling
│   ├── main.py                    ← Top-level Uvicorn startup shim
│   ├── run.py                     ← SmartAppControl-compatible Python launcher
│   ├── requirements.txt           ← Backend dependencies
│   └── .env.example               ← Environment configuration template
├── data-pipeline/                 ← Standalone Crime Data ETL & H3 Aggregator
│   ├── pipeline/                  ← Cleaners, normalizers, geocoder, H3 aggregator
│   └── requirements.txt
├── ml-risk/                       ← Machine Learning Area-Risk Model
│   ├── src/                       ← Features, calibrated baseline, evaluation
│   ├── train.py                   ← Model training CLI
│   └── requirements.txt
├── docs/                          ← Comprehensive Documentation
│   ├── architecture.md            ← System design & data flow diagrams
│   ├── api-documentation.md       ← Full REST API specifications
│   ├── setup-guide.md             ← Developer environment setup
│   ├── team-onboarding.md         ← Teammate step-by-step onboarding guide
│   ├── SECURITY_AUDIT_REPORT.md   ← Security & privacy audit compliance
│   └── PRODUCTION_CHECKLIST.md    ← Deployment readiness verification
├── docker-compose.yml             ← PostgreSQL 15 + FastAPI orchestration
├── CONTRIBUTING.md                ← Branching model & contribution rules
├── .gitignore                     ← Multi-component Git exclusion rules
└── README.md                      ← Master project documentation
```

---

## 5. Getting Started & Setup Instructions

### Prerequisites
* **Java:** OpenJDK 17 (or Android Studio JBR 17)
* **Android Studio:** Hedgehog (2023.1+) or newer with Android SDK Platform 34
* **Python:** 3.11+
* **Git:** 2.30+

---

### Step 1: Clone the Repository
```bash
git clone https://github.com/YOUR_ORGANIZATION/SafePath-AI.git
cd SafePath-AI
```

---

### Step 2: Configure & Start the Backend
```bash
cd backend

# Create virtual environment
python -m venv .venv

# Activate virtual environment
# Windows (PowerShell):
.\.venv\Scripts\Activate.ps1
# macOS / Linux:
source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Create your local environment file
cp .env.example .env

# Run FastAPI backend server (listens on 0.0.0.0:8000)
python run.py
```
* Interactive API Documentation (Swagger): [http://localhost:8000/docs](http://localhost:8000/docs)
* Health Check: [http://localhost:8000/health](http://localhost:8000/health)

---

### Step 3: Configure Android API Base URL

Open `android-app/gradle.properties` (or copy from `gradle.properties.example`):

#### A. When Testing on Android Studio Emulator:
```properties
LAPTOP_IPV4_ADDRESS=10.0.2.2
SERVER_PORT=8000
```
*(10.0.2.2 is the emulator's loopback alias mapping directly to your computer's localhost)*

#### B. When Testing on a Physical Phone via Wi-Fi / Mobile Hotspot:
1. Connect your phone and laptop to the same Wi-Fi network or phone mobile hotspot.
2. Find your laptop's IPv4 address:
   * **Windows:** Run `ipconfig` in PowerShell $\rightarrow$ look under **Wireless LAN adapter Wi-Fi** for `IPv4 Address` (e.g. `192.168.1.100` or `10.43.20.214`).
   * **macOS / Linux:** Run `ifconfig` or `ip addr`.
3. In `android-app/gradle.properties`:
   ```properties
   LAPTOP_IPV4_ADDRESS=YOUR_LAPTOP_IP_HERE
   SERVER_PORT=8000
   ```
4. Allow port 8000 through your firewall (Windows PowerShell as Admin):
   ```powershell
   New-NetFirewallRule -DisplayName "SafePath Backend" -Direction Inbound -LocalPort 8000 -Protocol TCP -Action Allow
   ```

#### C. When Testing on a Physical Phone via USB Cable (`adb reverse`):
1. Connect phone via USB with USB Debugging enabled.
2. In your terminal, run:
   ```bash
   adb reverse tcp:8000 tcp:8000
   ```
3. In `android-app/gradle.properties`:
   ```properties
   LAPTOP_IPV4_ADDRESS=127.0.0.1
   SERVER_PORT=8000
   ```

---

### Step 4: Build & Run the Android App
1. Open Android Studio $\rightarrow$ **Open** $\rightarrow$ select `SafePath-AI/android-app`.
2. Sync Project with Gradle Files (elephant icon).
3. Select your device (Emulator or Physical Phone) in the device toolbar.
4. Click **Run (Play)** $\blacktriangleright$ (`Shift + F10`).

---

## 6. Permissions & Safety Configuration

The application requests the following runtime permissions strictly when needed:
* **`ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`:** Required for GPS telemetry broadcast during active journeys and SOS alerts.
* **`ACCESS_BACKGROUND_LOCATION`:** Disclosed to the user to maintain journey safety tracking when the screen is locked.
* **`RECORD_AUDIO`:** Used strictly on-device by `VoiceRecognitionService` for local acoustic keyword detection. Never transmitted or uploaded.
* **`POST_NOTIFICATIONS`:** Used to display ongoing safety tracking status.

---

## 7. Automated Testing

### Backend Unit & Integration Tests:
```bash
cd backend
pytest -v
```

### Machine Learning Unit Tests:
```bash
cd ml-risk
pytest -v tests/
```

### Crime Data Pipeline Tests:
```bash
cd data-pipeline
pytest -v tests/
```

---

## 8. Known Limitations & Operating Guidance
* **Offline Audio:** Speech recognition availability depends on device Google Speech Services offline language pack support.
* **Battery Optimization:** Certain OEM Android distributions (e.g. MIUI, ColorOS) aggressively terminate background services; users should exclude SafePath AI from battery optimization for continuous background codeword monitoring.
* **SMS Dispatch:** In local development, emergency SMS alerts log to terminal; production deployment requires setting SMS gateway credentials (Twilio / AWS SNS / MSG91) in `backend/.env`.

---

## 9. Team Collaboration & Git Workflow
We follow a GitFlow branching model:
* **`main`**: Production-ready, always buildable. Direct commits are disallowed.
* **`develop`**: Integration branch for approved features.
* **`feature/<name>`**: Individual feature work.

See [CONTRIBUTING.md](file:///c:/Users/Asus/OneDrive/Desktop/AIML%20PROJECT/SafePathAI/CONTRIBUTING.md) and [team-onboarding.md](file:///c:/Users/Asus/OneDrive/Desktop/AIML%20PROJECT/SafePathAI/docs/team-onboarding.md) for full contribution guidelines.
