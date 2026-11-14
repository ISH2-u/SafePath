# SafePath AI — Developer Setup & Configuration Guide

This guide details configuring and running the complete SafePath AI platform across all environments.

---

## 1. System Requirements

* **Operating System:** Windows 10/11, macOS Monterey+, or Ubuntu 22.04+ LTS
* **Java:** OpenJDK 17 or JBR 17 (Required for Android build)
* **Android SDK:** API 34 (Android 14) with Build Tools 34.0.0
* **Python:** 3.11 or 3.12 (with pip and virtualenv)
* **Docker & Docker Compose:** (Optional, for running PostgreSQL locally)

---

## 2. Setting Up the Backend

```bash
# 1. Navigate to backend
cd backend

# 2. Create virtual environment
python -m venv .venv

# 3. Activate
# Windows:
.\.venv\Scripts\Activate.ps1
# Mac/Linux:
source .venv/bin/activate

# 4. Install dependencies
pip install -r requirements.txt

# 5. Create local environment file
cp .env.example .env

# 6. Run backend with automatic reload
python run.py
```

---

## 3. Configuring Android Networking for Local Testing

Because Android devices run in isolated network stacks, choosing the right host IP is essential:

| Testing Target | Recommended `LAPTOP_IPV4_ADDRESS` in `gradle.properties` | Notes |
| :--- | :--- | :--- |
| **Android Studio Emulator** | `10.0.2.2` | Emulator virtual gateway mapping to laptop's `127.0.0.1`. |
| **Physical Phone via Mobile Hotspot** | Your Laptop's Wi-Fi IP (e.g. `10.43.20.214` or `192.168.x.x`) | Requires opening port 8000 in Windows Firewall. |
| **Physical Phone via USB Cable** | `127.0.0.1` | Run `adb reverse tcp:8000 tcp:8000` in terminal. |

---

## 4. Running the Machine Learning Component

```bash
cd ml-risk

# Install dependencies
pip install -r requirements.txt

# Train spatial risk calibration model
python train.py

# Run unit tests
pytest tests/
```

---

## 5. Running the Crime Data Pipeline

```bash
cd data-pipeline

# Install dependencies
pip install -r requirements.txt

# Execute validation and deduplication pipeline
python -m pipeline.run

# Run unit tests
pytest tests/
```
