# SafePath AI — Local Backend & Android USB Demo Guide

This guide details the end-to-end setup for running the complete SafePath AI system locally on macOS with native PostgreSQL, and running the native Android application on a physical phone connected via USB using **ADB reverse port forwarding**.

---

## 1. System Architecture (Local USB Demonstration)

```
 [ Android Phone (Physical) ]
        │  USB Cable
        ▼  ADB Reverse (tcp:8000 -> tcp:8000)
 [ MacBook / Host ]
   ├── Port 8000: FastAPI Backend (Uvicorn + ML Risk Engine)
   └── Port 5432: PostgreSQL 16 (safepath_local database)
```

No cloud services, Render deployments, Neon databases, or Wi-Fi hotspot connections are needed. The phone communicates directly with your laptop's local backend over USB.

---

## 2. PostgreSQL Setup on macOS

### Check & Start PostgreSQL Service
PostgreSQL 16 is installed via Homebrew. Verify and start the service:

```bash
# Check service status
brew services list | grep postgresql

# Start PostgreSQL service if stopped
brew services start postgresql@16
```

### Initialize Database and User (One-time Setup)
The database cluster is already initialized. To verify or recreate the database:

```bash
# Create user 'postgres' with password 'postgres' (if not existing)
/opt/homebrew/opt/postgresql@16/bin/psql -h localhost -d postgres -c "
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'postgres') THEN
    CREATE ROLE postgres WITH SUPERUSER LOGIN PASSWORD 'postgres';
  END IF;
END
\$\$;"

# Create database 'safepath_local'
/opt/homebrew/opt/postgresql@16/bin/createdb -h localhost -U postgres safepath_local 2>/dev/null || true
```

### Run Schema Migrations
Apply the initial schema migrations covering `users`, `emergency_contacts`, `journeys`, and `sos_events`:

```bash
cd backend
.venv/bin/alembic upgrade head
```

---

## 3. Starting the Local FastAPI Backend

The backend environment is configured in `backend/.env`:
```ini
PROJECT_NAME="SafePath AI Backend"
API_V1_STR="/api/v1"
DEBUG=true
ENVIRONMENT=development
ALLOWED_ORIGINS=*
DATABASE_URL=postgresql+psycopg2://postgres:postgres@localhost:5432/safepath_local
SECRET_KEY=safepath_local_super_secret_demo_key_32bytes_min
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=1440
```

### Launch Backend Server
```bash
cd backend
.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### Verify Local Backend Health
In a separate terminal:
```bash
curl -s http://127.0.0.1:8000/health?check_db=true
```
Expected response:
```json
{
  "status": "healthy",
  "service": "SafePath AI Backend",
  "version": "1.0.0",
  "environment": "development",
  "database": "connected"
}
```

---

## 4. Connecting Android Device via USB (ADB Reverse)

### Step 1: Enable USB Debugging on Your Android Phone
1. Open phone **Settings** ➔ **About phone**.
2. Tap **Build number** 7 times to enable Developer options.
3. Go to **Settings** ➔ **Developer options**.
4. Turn on **USB debugging**.

### Step 2: Connect Phone to Mac via USB Cable
Plug the USB cable into your Mac and phone. When prompted on the phone screen, select **"Always allow from this computer"** and tap **Allow**.

### Step 3: Verify Device Detection & Configure ADB Reverse
Use the platform-tools ADB binary on your Mac:

```bash
# Add ADB to current shell PATH (optional, for convenience)
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

# Verify device is attached
adb devices
```
*Output should show your device ID followed by `device`.*

Now forward port 8000 from the phone to your Mac:
```bash
adb reverse tcp:8000 tcp:8000
```
*Output: `8000`.*

> [!NOTE]
> `adb reverse tcp:8000 tcp:8000` instructs Android that any network request from the phone to `http://127.0.0.1:8000` is transparently tunneled across the USB cable to your Mac's port `8000`.

---

## 5. Installing the Application on the Phone

### Option A: Install Pre-Built Debug APK
```bash
adb install -r ~/.android_builds/SafePathAI/app/outputs/apk/debug/app-debug.apk
```

### Option B: Clean Rebuild and Install
```bash
cd android-app
./gradlew :app:assembleDebug
adb install -r ~/.android_builds/SafePathAI/app/outputs/apk/debug/app-debug.apk
```

---

## 6. Testing the Demonstration Workflow

1. **Launch App**: Open **SafePath AI** from your phone app launcher.
2. **Register New Account**:
   - Tap **Create Account / Register**.
   - Fill in Name, Email, Phone, and Password.
   - Tap **Register** ➔ Successfully authenticates and enters Home dashboard.
3. **Emergency Contacts**:
   - Navigate to **Contacts** tab.
   - Tap **Add Emergency Contact** (e.g., "Mom", `+1234567890`).
   - Tap **Verify** to simulate the 6-digit OTP verification.
4. **Live Map & Area Risk Estimation**:
   - Open the **Map / Explore** screen.
   - The map loads and queries `GET /api/v1/risk/area?lat=...&lng=...`.
   - The embedded Calibrated ML Model scores the spatial risk and displays risk level and guidance.
5. **SOS Emergency Workflow**:
   - Press the **SOS** trigger button.
   - The backend records the event in `sos_events` table and dispatches alerts exclusively to verified emergency contacts.
   - Unverified contacts are strictly excluded by the safety guardrail.

---

## 7. Troubleshooting

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| `adb: device unauthorized` | Phone hasn't accepted Mac's RSA key | Unlock phone screen and tap **Allow** on the "Allow USB debugging?" dialog. |
| `adb: no devices/emulators found` | USB cable is charge-only or loose | Use a data-capable USB cable and ensure phone is unlocked. |
| `Failed to connect to /127.0.0.1:8000` | ADB reverse was not executed | Re-run: `adb reverse tcp:8000 tcp:8000`. Run every time the USB cable is reconnected. |
| `Database degraded / connection error` | PostgreSQL service is stopped | Run: `brew services start postgresql@16`. |
| Port 8000 already in use | Stale backend instance running | Find and kill PID: `lsof -ti :8000 \| xargs kill -9`. |
