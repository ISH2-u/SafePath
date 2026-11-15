from pydantic import BaseModel, ConfigDict
from typing import Optional, List, Dict, Any
from datetime import datetime


class SosTriggerRequest(BaseModel):
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    accuracy: Optional[float] = None
    is_last_known: bool = False
    trigger_type: str = "MANUAL"
    idempotency_key: str
    battery_level: Optional[int] = None
    notes: Optional[str] = None


class RecipientDeliveryStatus(BaseModel):
    contact_id: str
    contact_name: str
    phone: str
    channel: str  # SMS | PUSH
    status: str   # DELIVERED | PENDING | FAILED
    dispatched_at: datetime


class SosEventOut(BaseModel):
    id: str
    user_id: str
    latitude: Optional[float]
    longitude: Optional[float]
    accuracy: Optional[float]
    is_last_known: bool
    trigger_type: str
    status: str
    delivery_attempts: int
    recipients_count: int
    delivered_count: int
    message_body: Optional[str]
    triggered_at: datetime
    recipients: List[RecipientDeliveryStatus] = []
    is_idempotent_replay: bool = False
    disclaimer: str = "Emergency notification sent exclusively to user-verified contacts. Authorities and strangers were not alerted."

    model_config = ConfigDict(from_attributes=True)
