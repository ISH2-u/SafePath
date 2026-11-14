# SafePath AI — API Specification

The SafePath AI Backend provides a RESTful interface mounted at `/api/v1` (with root health checks). All endpoints return JSON and require Bearer token authentication except where explicitly noted.

---

## 1. Health Endpoints

### `GET /health`
* **Auth Required:** No
* **Description:** Top-level operational health probe.
* **Response (200 OK):**
  ```json
  {
    "status": "healthy",
    "service": "SafePath AI Backend",
    "version": "1.0.0"
  }
  ```

### `GET /api/v1/health`
* **Auth Required:** No
* **Description:** Subsystem health probe.

---

## 2. Authentication & Profile (`/api/v1/auth`)

### `POST /api/v1/auth/register`
* **Auth Required:** No
* **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "Password123!",
    "display_name": "Jane Doe",
    "phone": "+919876543210"
  }
  ```
* **Response (201 Created):**
  ```json
  {
    "access_token": "eyJhbGciOi...",
    "token_type": "bearer",
    "user": {
      "id": "uuid-here",
      "email": "user@example.com",
      "display_name": "Jane Doe",
      "phone": "+919876543210",
      "is_active": true,
      "created_at": "2026-11-14T10:00:00Z"
    }
  }
  ```

### `POST /api/v1/auth/login`
* **Auth Required:** No
* **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "Password123!"
  }
  ```
* **Response (200 OK):** Returns JWT access token and user profile.

### `GET /api/v1/auth/me`
* **Auth Required:** Yes (`Bearer <token>`)
* **Response (200 OK):** Current user profile details.

### `PUT /api/v1/auth/me`
* **Auth Required:** Yes
* **Request Body:**
  ```json
  {
    "display_name": "Jane Doe",
    "phone": "+919876543210",
    "blood_group": "O+",
    "medical_notes": "Allergic to penicillin"
  }
  ```

### `DELETE /api/v1/auth/me`
* **Auth Required:** Yes
* **Request Body:**
  ```json
  {
    "confirm": true,
    "password": "CurrentPassword123!"
  }
  ```

---

## 3. Emergency Contacts (`/api/v1/contacts`)

### `GET /api/v1/contacts`
* **Auth Required:** Yes
* **Description:** Lists all emergency contacts configured by the user.

### `POST /api/v1/contacts`
* **Auth Required:** Yes
* **Request Body:**
  ```json
  {
    "name": "Alex Smith",
    "phone": "+919876543211",
    "email": "alex@example.com",
    "relationship_type": "Family",
    "receive_sos_alerts": true
  }
  ```

### `POST /api/v1/contacts/{contact_id}/verify`
* **Auth Required:** Yes
* **Request Body:**
  ```json
  {
    "code": "123456"
  }
  ```

### `PATCH /api/v1/contacts/{contact_id}/toggle-sos`
* **Auth Required:** Yes
* **Request Body:**
  ```json
  {
    "receive_sos_alerts": true
  }
  ```

---

## 4. Emergency SOS (`/api/v1/sos`)

### `POST /api/v1/sos/trigger`
* **Auth Required:** Yes
* **Request Body:**
  ```json
  {
    "latitude": 28.6139,
    "longitude": 77.2090,
    "accuracy": 4.5,
    "is_last_known": false,
    "trigger_type": "MANUAL",
    "idempotency_key": "c8a4d70b-...",
    "battery_level": 85,
    "notes": "Manual SOS Trigger"
  }
  ```
* **Response (200 OK):**
  ```json
  {
    "id": "sos-event-uuid",
    "status": "DISPATCHED",
    "recipients_count": 2,
    "delivered_count": 2,
    "delivery_attempts": 1,
    "message_body": "EMERGENCY ALERT: Jane Doe has triggered SOS. Location: https://maps.google.com/?q=28.6139,77.2090"
  }
  ```

---

## 5. Area Risk & Safety Assistant

### `GET /api/v1/risk`
* **Parameters:** `lat`, `lng`, `radius_km`
* **Description:** Returns historical spatial risk index for the specified geographic coordinate.

### `POST /api/v1/assistant/chat`
* **Auth Required:** Yes
* **Request Body:**
  ```json
  {
    "message": "What should I do if walking alone at night?",
    "session_id": "session-uuid"
  }
  ```
* **Description:** Retrieves contextual safety guidance with citations.
