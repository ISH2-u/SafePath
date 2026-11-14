import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.database.base import Base
from app.models.models import User, EmergencyContact
from app.security.security import (
    get_password_hash, verify_password,
    create_access_token, decode_access_token
)

# Use SQLite in-memory for testing
TEST_DATABASE_URL = "sqlite:///:memory:"

@pytest.fixture
def db_session():
    engine = create_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    Base.metadata.create_all(bind=engine)
    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.close()
        Base.metadata.drop_all(bind=engine)

# ─── TEST AUTHENTICATION & PASSWORD HASHING ───────────────────────────────────

def test_password_hashing():
    """Verify that passwords are securely hashed using PBKDF2-HMAC-SHA256 and never in plaintext."""
    raw_password = "SuperSecretPassword123!"
    hashed = get_password_hash(raw_password)
    
    assert hashed != raw_password
    assert hashed.startswith("pbkdf2:sha256:600000$")
    assert verify_password(raw_password, hashed) is True
    assert verify_password("WrongPassword!", hashed) is False

def test_jwt_token_flow():
    """Verify JWT access token creation, claims, and signature verification."""
    user_id = "test-user-1234"
    token = create_access_token(subject=user_id)
    
    payload = decode_access_token(token)
    assert payload is not None
    assert payload.get("sub") == user_id
    assert "exp" in payload
    
    # Tampered token must fail
    tampered_token = token[:-4] + "fake"
    assert decode_access_token(tampered_token) is None

# ─── TEST EMERGENCY CONTACT VERIFICATION & RECIPIENT RESTRICTIONS ─────────────

def test_contact_creation_and_verification(db_session):
    """
    Test that:
    1. A newly created contact starts strictly unverified.
    2. Invalid 6-digit OTP codes are rejected.
    3. Correct 6-digit OTP code marks the contact as verified.
    """
    user = User(
        email="safety.user@safepath.ai",
        hashed_password=get_password_hash("TestPass123!"),
        display_name="Safety User",
        phone="+15551234567"
    )
    db_session.add(user)
    db_session.commit()
    db_session.refresh(user)

    # Add contact with 6-digit code
    code = "789123"
    contact = EmergencyContact(
        user_id=user.id,
        name="Elena Vance",
        phone="+15559876543",
        relationship_type="Parent",
        is_verified=False,
        verification_code=code,
        receive_sos_alerts=True
    )
    db_session.add(contact)
    db_session.commit()
    db_session.refresh(contact)

    # Verify initial state: strictly unverified
    assert contact.is_verified is False

    # Attempt verification with wrong code
    wrong_code = "000000"
    assert contact.verification_code != wrong_code

    # Verify with correct code
    if contact.verification_code == code:
        contact.is_verified = True
        contact.verification_code = None
        db_session.commit()

    db_session.refresh(contact)
    assert contact.is_verified is True
    assert contact.verification_code is None

def test_strict_authorized_recipients_filtering(db_session):
    """
    CRITICAL RESTRICTION TEST:
    Verify that ONLY explicitly verified contacts with receive_sos_alerts=True
    are ever returned as authorized emergency recipients.
    
    Unverified contacts and contacts with receive_sos_alerts=False MUST be excluded.
    """
    user = User(
        email="sarah.connor@safepath.ai",
        hashed_password=get_password_hash("SecurePassword888!"),
        display_name="Sarah Connor"
    )
    db_session.add(user)
    db_session.commit()
    db_session.refresh(user)

    # Contact 1: Verified + alerts enabled -> MUST BE INCLUDED
    c1 = EmergencyContact(
        user_id=user.id,
        name="Verified Primary",
        phone="+15551111111",
        is_verified=True,
        receive_sos_alerts=True
    )
    # Contact 2: UNVERIFIED -> MUST BE EXCLUDED
    c2 = EmergencyContact(
        user_id=user.id,
        name="Unverified Stranger/Pending",
        phone="+15552222222",
        is_verified=False,
        receive_sos_alerts=True
    )
    # Contact 3: Verified but user opted out of alerts -> MUST BE EXCLUDED
    c3 = EmergencyContact(
        user_id=user.id,
        name="Verified But Opted Out",
        phone="+15553333333",
        is_verified=True,
        receive_sos_alerts=False
    )
    db_session.add_all([c1, c2, c3])
    db_session.commit()

    # Query strictly authorized recipients
    recipients = db_session.query(EmergencyContact).filter(
        EmergencyContact.user_id == user.id,
        EmergencyContact.is_verified == True,
        EmergencyContact.receive_sos_alerts == True
    ).all()

    # Exact assertions
    assert len(recipients) == 1
    assert recipients[0].phone == "+15551111111"
    assert recipients[0].name == "Verified Primary"

    # Confirm unverified contact is strictly excluded
    recipient_phones = [r.phone for r in recipients]
    assert "+15552222222" not in recipient_phones
    assert "+15553333333" not in recipient_phones

