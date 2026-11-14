# SafePath AI — Teammate Onboarding Guide

Welcome to the **SafePath AI** team! This guide walks you through setting up your complete local environment from scratch on Windows, macOS, or Linux, ensuring you can build, run, test, and contribute without depending on anyone else's machine.

---

## 1. Prerequisites Installation

### A. Install Git
* **Windows:** Download and install [Git for Windows](https://git-scm.com/download/win). Keep defaults, including Git Credential Manager.
* **macOS:** `brew install git`
* **Linux:** `sudo apt-get install git`

Configure your Git identity:
```bash
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

### B. Install Android Studio & JDK
1. Download and install [Android Studio](https://developer.android.com/studio) (Hedgehog, Iguana, or later).
2. During setup, install:
   * **Android SDK Platform 34** (Android 14)
   * **Android SDK Build-Tools 34**
   * **Android SDK Command-line Tools**
   * **Android Emulator**
3. Open Android Studio $\rightarrow$ **Settings** $\rightarrow$ **Build, Execution, Deployment** $\rightarrow$ **Build Tools** $\rightarrow$ **Gradle** $\rightarrow$ Verify **Gradle JDK** is set to **Java 17** (or bundled JBR 17).

### C. Install Python
1. Install **Python 3.11+** from [python.org](https://www.python.org/downloads/) (or via Homebrew/apt).
2. On Windows, check the box: **"Add python.exe to PATH"**.

---

## 2. Obtain Repository Access & Clone

1. Provide your GitHub username to the repository owner to be added as a collaborator on the private **`SafePath-AI`** repository.
2. Clone the repository to your local computer:
   ```bash
   git clone https://github.com/YOUR_ORGANIZATION/SafePath-AI.git
   cd SafePath-AI
   ```

---

## 3. Backend Setup (FastAPI & Database)

### A. Create Python Virtual Environment
Open a terminal in the project root:
```bash
cd backend
python -m venv .venv

# Activate virtual environment:
# On Windows (PowerShell):
.\.venv\Scripts\Activate.ps1
# On macOS / Linux:
source .venv/bin/activate
```

### B. Install Dependencies
```bash
pip install --upgrade pip
pip install -r requirements.txt
```

### C. Configure Environment Variables
Copy the template `.env.example` to create your own private `.env` file:
```bash
# On Windows (PowerShell):
Copy-Item .env.example .env
# On macOS / Linux:
cp .env.example .env
```
Open `backend/.env` in your editor. By default, it is configured for local **SQLite** (`DATABASE_URL=sqlite:///./safepath.db`), which requires zero setup!

*(Optional: If you wish to use PostgreSQL, start Docker: `docker-compose up -d db` and set `DATABASE_URL=postgresql+psycopg2://safepath_user:safepath_pass@localhost:5432/safepath_db`)*.

### D. Start the Backend Server
```bash
python run.py
```
*(Or run `uvicorn main:app --host 0.0.0.0 --port 8000 --reload`)*

Test that it works by opening in your browser:
* Health Check: [http://localhost:8000/health](http://localhost:8000/health)
* Interactive Swagger Docs: [http://localhost:8000/docs](http://localhost:8000/docs)

---

## 4. Android Application Setup

### A. Open Project in Android Studio
1. Open Android Studio.
2. Click **Open** and select the **`SafePath-AI/android-app`** directory (not the root directory!).
3. Android Studio will automatically initialize and download Gradle dependencies.

### B. Configure Your Local Server Connection
Open `android-app/gradle.properties` (or copy from `gradle.properties.example`):

**Option 1: Android Studio Emulator (Easiest)**
```properties
LAPTOP_IPV4_ADDRESS=10.0.2.2
SERVER_PORT=8000
```
*(10.0.2.2 automatically routes to your computer's localhost from the emulator)*

**Option 2: Physical Android Phone via Wi-Fi / Mobile Hotspot**
1. Connect both phone and laptop to the same Wi-Fi / Hotspot.
2. Find your laptop's IPv4 address:
   * **Windows:** Run `ipconfig` in PowerShell $\rightarrow$ look for Wireless LAN adapter Wi-Fi IPv4 Address (e.g. `192.168.1.15`).
   * **macOS / Linux:** Run `ifconfig` or `ip addr`.
3. Set in `gradle.properties`:
   ```properties
   LAPTOP_IPV4_ADDRESS=YOUR_LAPTOP_IP_HERE
   SERVER_PORT=8000
   ```
4. Allow port 8000 through your OS firewall:
   * **Windows:** `New-NetFirewallRule -DisplayName "FastAPI" -Direction Inbound -LocalPort 8000 -Protocol TCP -Action Allow`

**Option 3: Physical Android Phone via USB Cable (`adb reverse`)**
1. Connect phone with USB debugging enabled.
2. Run in terminal: `adb reverse tcp:8000 tcp:8000`
3. Set in `gradle.properties`:
   ```properties
   LAPTOP_IPV4_ADDRESS=127.0.0.1
   SERVER_PORT=8000
   ```

### C. Build and Run
1. Select your target device (Emulator or Physical Device) in the Android Studio toolbar.
2. Click **Run (Play)** $\blacktriangleright$ (`Shift + F10`).
3. The app will launch with `SplashActivity` $\rightarrow$ `LoginActivity`.
4. Click **"Create Account"** to register a test user against your local backend.

---

## 5. Development & Contribution Rules

1. **Always work in a feature branch:**
   ```bash
   git checkout -b feature/your-task-name
   ```
2. **Never commit credentials:** Keep `.env`, `safepath.db`, and local device IPs out of Git.
3. **Run tests before pushing:**
   ```bash
   cd backend && pytest
   cd ../ml-risk && pytest
   ```
4. **Push your branch and open a Pull Request:**
   ```bash
   git push -u origin feature/your-task-name
   ```
   Open a PR against `develop` (or `main`) and tag a teammate for review.
