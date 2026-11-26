import os
import re
import uuid
import logging
from typing import Dict, Any, Optional
import requests
from app.config import settings

logger = logging.getLogger("safepath.sms")


def mask_phone_number(phone: str) -> str:
    """
    Masks a phone number for secure logging and display to prevent exposing
    PII or sensitive phone numbers.
    Example: '+91 98765 43210' -> '+91 ******3210'
    """
    if not phone:
        return "[EMPTY]"
    clean = phone.strip()
    if len(clean) <= 4:
        return "****"
    prefix = clean[:3] if clean.startswith("+") else clean[:2]
    suffix = clean[-4:]
    return f"{prefix} ******{suffix}"


def normalize_phone_number(phone: str) -> str:
    """
    Normalizes phone numbers to standard E.164.
    If a 10-digit Indian mobile number (starting with 6-9) is given,
    it automatically prepends '+91'.
    """
    if not phone:
        return ""
    trimmed = phone.strip()
    has_plus = trimmed.startswith("+")
    digits_only = re.sub(r"[^0-9]", "", trimmed)
    if not digits_only:
        return ""

    if has_plus:
        return f"+{digits_only}"

    if len(digits_only) == 11 and digits_only.startswith("0"):
        digits_only = digits_only[1:]

    # 10-digit Indian mobile number
    if len(digits_only) == 10 and digits_only[0] in "6789":
        return f"+91{digits_only}"

    if len(digits_only) == 12 and digits_only.startswith("91"):
        return f"+{digits_only}"

    return digits_only


class SmsService:
    """
    Unified SMS delivery provider service.
    Supports Twilio, Fast2SMS, and DEV_MOCK fallbacks.
    Sensitive numbers and OTPs are never exposed in logs.
    """

    def __init__(self):
        self.provider = (settings.SMS_PROVIDER or "DEV_MOCK").upper()
        self.twilio_sid = settings.TWILIO_ACCOUNT_SID or os.getenv("TWILIO_ACCOUNT_SID")
        self.twilio_token = settings.TWILIO_AUTH_TOKEN or os.getenv("TWILIO_AUTH_TOKEN")
        self.twilio_from = settings.TWILIO_FROM_NUMBER or os.getenv("TWILIO_FROM_NUMBER")
        self.fast2sms_key = settings.FAST2SMS_API_KEY or os.getenv("FAST2SMS_API_KEY")

        # Auto-detect real provider if credentials configured
        if self.provider == "DEV_MOCK":
            if self.twilio_sid and self.twilio_token and self.twilio_from:
                self.provider = "TWILIO"
            elif self.fast2sms_key:
                self.provider = "FAST2SMS"

    def send_verification_otp(self, phone: str, otp_code: str) -> Dict[str, Any]:
        """
        Dispatches a 6-digit OTP code to an emergency contact for phone verification.
        """
        norm_phone = normalize_phone_number(phone)
        masked = mask_phone_number(norm_phone)
        message = (
            f"SafePath AI verification code: {otp_code}. "
            f"This code will expire in 10 minutes. Do not share."
        )

        logger.info(f"Dispatching OTP verification to {masked} via provider={self.provider}")

        if self.provider == "TWILIO" and self.twilio_sid and self.twilio_token and self.twilio_from:
            return self._send_twilio(norm_phone, message, masked)
        elif self.provider == "FAST2SMS" and self.fast2sms_key:
            return self._send_fast2sms(norm_phone, message, masked)
        else:
            return self._send_mock(norm_phone, message, masked, purpose="OTP_VERIFICATION")

    def send_sos_alert(self, phone: str, alert_message: str) -> Dict[str, Any]:
        """
        Dispatches an emergency SOS broadcast to a verified emergency contact.
        """
        norm_phone = normalize_phone_number(phone)
        masked = mask_phone_number(norm_phone)

        logger.info(f"Dispatching SOS alert to {masked} via provider={self.provider}")

        if self.provider == "TWILIO" and self.twilio_sid and self.twilio_token and self.twilio_from:
            return self._send_twilio(norm_phone, alert_message, masked)
        elif self.provider == "FAST2SMS" and self.fast2sms_key:
            return self._send_fast2sms(norm_phone, alert_message, masked)
        else:
            return self._send_mock(norm_phone, alert_message, masked, purpose="SOS_ALERT")

    def _send_twilio(self, to_phone: str, body: str, masked_phone: str) -> Dict[str, Any]:
        url = f"https://api.twilio.com/2010-04-01/Accounts/{self.twilio_sid}/Messages.json"
        try:
            resp = requests.post(
                url,
                data={"From": self.twilio_from, "To": to_phone, "Body": body},
                auth=(self.twilio_sid, self.twilio_token),
                timeout=10,
            )
            if resp.status_code in (200, 201):
                data = resp.json()
                logger.info(f"Twilio SMS dispatched successfully to {masked_phone}, sid={data.get('sid')}")
                return {
                    "status": "DELIVERED",
                    "provider": "TWILIO",
                    "message_id": data.get("sid"),
                    "delivered": True,
                }
            else:
                logger.error(f"Twilio SMS failed to {masked_phone}: {resp.status_code} - {resp.text}")
                return {
                    "status": "FAILED",
                    "provider": "TWILIO",
                    "error": resp.text,
                    "delivered": False,
                }
        except Exception as e:
            logger.exception(f"Twilio connection error for {masked_phone}: {str(e)}")
            return {
                "status": "FAILED",
                "provider": "TWILIO",
                "error": str(e),
                "delivered": False,
            }

    def _send_fast2sms(self, to_phone: str, body: str, masked_phone: str) -> Dict[str, Any]:
        # Fast2SMS requires 10-digit Indian numbers without +91
        clean_num = re.sub(r"[^0-9]", "", to_phone)
        if clean_num.startswith("91") and len(clean_num) == 12:
            clean_num = clean_num[2:]

        url = "https://www.fast2sms.com/dev/bulkV2"
        headers = {"authorization": self.fast2sms_key, "Content-Type": "application/json"}
        payload = {
            "route": "v3",
            "sender_id": "TXTIND",
            "message": body,
            "language": "english",
            "flash": 0,
            "numbers": clean_num,
        }
        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=10)
            if resp.status_code == 200 and resp.json().get("return"):
                logger.info(f"Fast2SMS dispatched successfully to {masked_phone}")
                return {
                    "status": "DELIVERED",
                    "provider": "FAST2SMS",
                    "message_id": f"f2s-{uuid.uuid4().hex[:8]}",
                    "delivered": True,
                }
            else:
                logger.error(f"Fast2SMS failed to {masked_phone}: {resp.status_code} - {resp.text}")
                return {
                    "status": "FAILED",
                    "provider": "FAST2SMS",
                    "error": resp.text,
                    "delivered": False,
                }
        except Exception as e:
            logger.exception(f"Fast2SMS connection error for {masked_phone}: {str(e)}")
            return {
                "status": "FAILED",
                "provider": "FAST2SMS",
                "error": str(e),
                "delivered": False,
            }

    def _send_mock(self, to_phone: str, body: str, masked_phone: str, purpose: str) -> Dict[str, Any]:
        mock_id = f"mock-{uuid.uuid4().hex[:8]}"
        logger.info(
            f"[DEV_MOCK SMS DISPATCH] Purpose: {purpose} | To: {masked_phone} | MsgId: {mock_id} | "
            f"Length: {len(body)} chars | Note: Local simulated delivery"
        )
        return {
            "status": "MOCK_SENT",
            "provider": "DEV_MOCK",
            "message_id": mock_id,
            "delivered": True,
            "note": "Local development mock delivery. Set TWILIO or FAST2SMS credentials for live SMS.",
        }


sms_service = SmsService()
