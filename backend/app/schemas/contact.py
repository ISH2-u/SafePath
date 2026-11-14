from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, Field


class ContactCreate(BaseModel):
    name: str = Field(..., min_length=1, description="Contact full name")
    phone: str = Field(..., min_length=6, description="Contact phone number with country code")
    email: Optional[str] = None
    relationship_type: Optional[str] = Field(None, description="Relationship: Parent, Spouse, Sibling, Friend, etc.")
    receive_sos_alerts: bool = Field(True, description="Whether to include in emergency notifications once verified")


class ContactUpdate(BaseModel):
    name: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None
    relationship_type: Optional[str] = None
    receive_sos_alerts: Optional[bool] = None


class ContactVerifyRequest(BaseModel):
    code: str = Field(..., min_length=6, max_length=6, description="6-digit verification code")


class ContactToggleAlerts(BaseModel):
    receive_sos_alerts: bool


class ContactOut(BaseModel):
    id: str
    user_id: str
    name: str
    phone: str
    email: Optional[str] = None
    relationship_type: Optional[str] = None
    is_verified: bool
    receive_sos_alerts: bool
    added_at: datetime
    updated_at: Optional[datetime] = None

    class Config:
        from_attributes = True


class VerificationDispatchResponse(BaseModel):
    contact_id: str
    phone: str
    status: str
    message: str
    # In development/test mode, verification code is included to allow easy testing
    dev_verification_code: Optional[str] = None


class AuthorizedRecipient(BaseModel):
    """Represents a strictly verified recipient permitted to receive emergency SOS alerts."""
    contact_id: str
    name: str
    phone: str
    relationship_type: Optional[str] = None
    is_verified: bool = True
    receive_sos_alerts: bool = True


class AuthorizedRecipientsResponse(BaseModel):
    user_id: str
    total_recipients: int
    recipients: List[AuthorizedRecipient]
    policy_notice: str = "Strict Policy: Alerts are delivered ONLY to these verified personal contacts. Authorities/strangers are never included."

