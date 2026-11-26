import pytest
from app.services.sms_service import normalize_phone_number, mask_phone_number, sms_service


def test_normalize_phone_number():
    # Indian 10-digit mobile starting with 6-9
    assert normalize_phone_number("9876543210") == "+919876543210"
    assert normalize_phone_number("8123456789") == "+918123456789"
    assert normalize_phone_number("7000000000") == "+917000000000"
    assert normalize_phone_number("6999999999") == "+916999999999"

    # Leading zero 11-digit
    assert normalize_phone_number("09876543210") == "+919876543210"

    # Already with +91
    assert normalize_phone_number("+919876543210") == "+919876543210"
    assert normalize_phone_number("+91 98765 43210") == "+919876543210"

    # International
    assert normalize_phone_number("+1 (555) 123-4567") == "+15551234567"


def test_mask_phone_number():
    masked = mask_phone_number("+919876543210")
    assert "+91" in masked
    assert "3210" in masked
    assert "98765" not in masked
    assert "******" in masked


def test_sms_mock_dispatch():
    res_otp = sms_service.send_verification_otp("+919876543210", "123456")
    assert res_otp["delivered"] is True
    assert res_otp["status"] in ("MOCK_SENT", "DELIVERED")

    res_sos = sms_service.send_sos_alert("+919876543210", "Emergency alert test message")
    assert res_sos["delivered"] is True
    assert res_sos["status"] in ("MOCK_SENT", "DELIVERED")
