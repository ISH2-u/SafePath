import os
import secrets
import hashlib
import hmac
import base64
import json
from datetime import datetime, timedelta, timezone
from typing import Optional, Dict, Any
from app.config import settings

# ─── SECURE PASSWORD HASHING (OWASP RECOMMENDED PBKDF2-HMAC-SHA256) ───────────
# 600,000 iterations of HMAC-SHA256 with 16-byte random salt per user password.
# Passwords are NEVER stored in plaintext.

ITERATIONS = 600000
HASH_NAME = "sha256"

def get_password_hash(password: str) -> str:
    """Generate a secure cryptographic hash using PBKDF2-HMAC-SHA256 with random salt."""
    salt = secrets.token_hex(16)
    key = hashlib.pbkdf2_hmac(
        HASH_NAME,
        password.encode("utf-8"),
        salt.encode("utf-8"),
        ITERATIONS
    )
    return f"pbkdf2:{HASH_NAME}:{ITERATIONS}${salt}${key.hex()}"

def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify a plaintext password against the stored cryptographic hash."""
    try:
        header, salt, stored_key = hashed_password.split("$")
        algo_info = header.split(":")
        iterations = int(algo_info[2])
        hash_algo = algo_info[1]
        
        computed_key = hashlib.pbkdf2_hmac(
            hash_algo,
            plain_password.encode("utf-8"),
            salt.encode("utf-8"),
            iterations
        )
        return hmac.compare_digest(computed_key.hex(), stored_key)
    except Exception:
        return False

# ─── JWT TOKEN GENERATION & VERIFICATION ──────────────────────────────────────

def _b64encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode('ascii')

def _b64decode(data: str) -> bytes:
    padding = 4 - (len(data) % 4)
    if padding != 4:
        data += '=' * padding
    return base64.urlsafe_b64decode(data.encode('ascii'))

def create_access_token(subject: str, expires_delta: Optional[timedelta] = None, extra_claims: Optional[Dict[str, Any]] = None) -> str:
    """Create a cryptographically signed HMAC-SHA256 JSON Web Token (JWT)."""
    if expires_delta:
        expire = datetime.now(timezone.utc) + expires_delta
    else:
        expire = datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    
    header = {"alg": settings.ALGORITHM, "typ": "JWT"}
    payload = {
        "sub": subject,
        "exp": int(expire.timestamp()),
        "iat": int(datetime.now(timezone.utc).timestamp()),
        "jti": secrets.token_hex(8)
    }
    if extra_claims:
        payload.update(extra_claims)

    encoded_header = _b64encode(json.dumps(header, separators=(',', ':')).encode('utf-8'))
    encoded_payload = _b64encode(json.dumps(payload, separators=(',', ':')).encode('utf-8'))
    
    signature_base = f"{encoded_header}.{encoded_payload}"
    signature = hmac.new(
        settings.SECRET_KEY.encode('utf-8'),
        signature_base.encode('utf-8'),
        hashlib.sha256
    ).digest()
    encoded_signature = _b64encode(signature)

    return f"{signature_base}.{encoded_signature}"

def decode_access_token(token: str) -> Optional[Dict[str, Any]]:
    """Verify cryptographic signature and expiration of JWT, returning payload if valid."""
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None
        
        encoded_header, encoded_payload, encoded_signature = parts
        signature_base = f"{encoded_header}.{encoded_payload}"
        
        expected_sig = hmac.new(
            settings.SECRET_KEY.encode('utf-8'),
            signature_base.encode('utf-8'),
            hashlib.sha256
        ).digest()
        
        actual_sig = _b64decode(encoded_signature)
        if not hmac.compare_digest(expected_sig, actual_sig):
            return None
        
        payload_bytes = _b64decode(encoded_payload)
        payload = json.loads(payload_bytes.decode('utf-8'))
        
        # Check expiration
        now_ts = int(datetime.now(timezone.utc).timestamp())
        if payload.get("exp", 0) < now_ts:
            return None
            
        return payload
    except Exception:
        return None

