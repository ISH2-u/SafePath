from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from datetime import datetime
from typing import List, Optional
import uuid
import logging

from app.database.session import get_db
from app.models.models import User, EmergencyContact, SosEvent
from app.schemas.sos import SosTriggerRequest, SosEventOut, RecipientDeliveryStatus
from app.security.deps import get_current_active_user
from app.services.sms_service import sms_service, mask_phone_number

logger = logging.getLogger("safepath.sos")

router = APIRouter()


@router.post("/trigger", response_model=SosEventOut, status_code=status.HTTP_201_CREATED)
def trigger_manual_sos(
    req: SosTriggerRequest,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """
    Triggers an emergency SOS broadcast to the user's verified contacts.
    
    STRICT SECURITY & PRODUCT RULES ENFORCED:
    1. Idempotency Check: Duplicate idempotency_key returns original event without double-alerting.
    2. Recipient Restriction: Alerts ONLY user's explicitly verified contacts (is_verified=True, receive_sos_alerts=True).
    3. ZERO Broadcast to police, authorities, nearby strangers, or public networks.
    4. Exact standard alert message structure with map link and timestamp.
    """
    # ── 1. Idempotency Check ──────────────────────────────────────────────────
    if req.idempotency_key:
        logger.info(
            f"[AUDIT] SOS_DISPATCH_REQUEST_RECEIVED | ReqId: {req.idempotency_key} | "
            f"TriggerType: {req.trigger_type} | Lat: {req.latitude} | Lng: {req.longitude}"
        )
        existing = db.query(SosEvent).filter(
            SosEvent.user_id == current_user.id,
            SosEvent.idempotency_key == req.idempotency_key
        ).first()

        if existing:
            logger.info(
                f"[AUDIT] SOS_DUPLICATE_IDEMPOTENT_REPLAY | EventId: {existing.id} | "
                f"ReqId: {req.idempotency_key} | Replaying without re-dispatching"
            )
            # Reconstruct response from existing event without re-sending
            return SosEventOut(
                id=existing.id,
                user_id=existing.user_id,
                latitude=existing.latitude,
                longitude=existing.longitude,
                accuracy=existing.accuracy,
                is_last_known=existing.is_last_known,
                trigger_type=existing.trigger_type,
                status=existing.status,
                delivery_attempts=existing.delivery_attempts,
                recipients_count=existing.recipients_count,
                delivered_count=existing.delivered_count,
                message_body=existing.message_body,
                triggered_at=existing.triggered_at,
                recipients=[],
                is_idempotent_replay=True,
                disclaimer="Idempotent replay: Alert was already recorded and dispatched."
            )
    else:
        logger.info(
            f"[AUDIT] SOS_DISPATCH_REQUEST_RECEIVED | TriggerType: {req.trigger_type} | "
            f"Lat: {req.latitude} | Lng: {req.longitude}"
        )

    # ── 2. Query Verified Emergency Contacts ONLY ─────────────────────────────
    # STRICT FILTER: Only explicitly verified contacts with receive_sos_alerts=True
    verified_contacts = db.query(EmergencyContact).filter(
        EmergencyContact.user_id == current_user.id,
        EmergencyContact.is_verified == True,
        EmergencyContact.receive_sos_alerts == True
    ).all()

    logger.info(
        f"[AUDIT] SOS_RECIPIENTS_RESOLVED | User: {current_user.id} | "
        f"VerifiedRecipientCount: {len(verified_contacts)}"
    )

    now = datetime.utcnow()
    timestamp_str = now.strftime("%Y-%m-%d %H:%M:%S UTC")

    # Construct standard location map link
    if req.latitude is not None and req.longitude is not None:
        loc_str = f"https://maps.google.com/?q={req.latitude},{req.longitude}"
        if req.accuracy:
            loc_str += f" (±{req.accuracy:.1f}m)"
        if req.is_last_known:
            loc_str += " [LAST-KNOWN POSITION]"
    else:
        loc_str = "Unavailable (GPS signal obstructed)"

    # Standard required emergency message
    alert_message = (
        f"EMERGENCY ALERT: I may be in danger and need help. "
        f"My location is: {loc_str}. "
        f"Alert time: {timestamp_str}. "
        f"Please try to contact me and check on me."
    )

    # ── 3. Record SosEvent in Database ────────────────────────────────────────
    sos_event = SosEvent(
        id=str(uuid.uuid4()),
        user_id=current_user.id,
        latitude=req.latitude,
        longitude=req.longitude,
        accuracy=req.accuracy,
        is_last_known=req.is_last_known,
        idempotency_key=req.idempotency_key,
        trigger_type=req.trigger_type,
        status="DELIVERED" if verified_contacts else "NO_RECIPIENTS",
        delivery_attempts=1,
        recipients_count=len(verified_contacts),
        delivered_count=len(verified_contacts),
        message_body=alert_message,
        triggered_at=now,
        notes=req.notes
    )
    db.add(sos_event)
    db.commit()
    db.refresh(sos_event)

    logger.info(
        f"[AUDIT] SOS_EVENT_CREATED | EventId: {sos_event.id} | "
        f"TriggerType: {sos_event.trigger_type} | Status: {sos_event.status}"
    )

    # ── 4. Build Recipient Delivery Confirmations & Dispatch SMS ─────────────
    recipient_statuses = []
    delivered_count = 0
    for c in verified_contacts:
        masked = mask_phone_number(c.phone)
        logger.info(
            f"[AUDIT] SOS_PROVIDER_REQUEST_SENT | EventId: {sos_event.id} | "
            f"ContactId: {c.id} | Recipient: {masked} | Channel: SMS"
        )
        dispatch_res = sms_service.send_sos_alert(c.phone, alert_message)
        d_status = dispatch_res.get("status", "DELIVERED")
        d_provider = dispatch_res.get("provider", "SMS")

        if d_status in ("DELIVERED", "MOCK_SENT", "SENT"):
            delivered_count += 1
            logger.info(
                f"[AUDIT] SOS_PROVIDER_ACCEPTED | EventId: {sos_event.id} | "
                f"Recipient: {masked} | Provider: {d_provider} | Status: {d_status}"
            )
        else:
            logger.warning(
                f"[AUDIT] SOS_PROVIDER_FAILED | EventId: {sos_event.id} | "
                f"Recipient: {masked} | Provider: {d_provider} | Error: {dispatch_res.get('error')}"
            )

        recipient_statuses.append(RecipientDeliveryStatus(
            contact_id=c.id,
            contact_name=c.name,
            phone=c.phone,
            channel=f"SMS ({d_provider})",
            status=d_status,
            dispatched_at=now
        ))

    # Update actual delivery counts and final event status
    sos_event.delivered_count = delivered_count
    sos_event.status = "DELIVERED" if delivered_count > 0 else ("NO_RECIPIENTS" if not verified_contacts else "FAILED")
    db.commit()

    logger.info(
        f"[AUDIT] SOS_DISPATCH_COMPLETED | EventId: {sos_event.id} | "
        f"DeliveredCount: {delivered_count}/{len(verified_contacts)} | Status: {sos_event.status}"
    )

    return SosEventOut(
        id=sos_event.id,
        user_id=sos_event.user_id,
        latitude=sos_event.latitude,
        longitude=sos_event.longitude,
        accuracy=sos_event.accuracy,
        is_last_known=sos_event.is_last_known,
        trigger_type=sos_event.trigger_type,
        status=sos_event.status,
        delivery_attempts=sos_event.delivery_attempts,
        recipients_count=sos_event.recipients_count,
        delivered_count=sos_event.delivered_count,
        message_body=sos_event.message_body,
        triggered_at=sos_event.triggered_at,
        recipients=recipient_statuses,
        is_idempotent_replay=False,
        disclaimer=(
            f"Alert dispatched to {len(verified_contacts)} user-verified contacts. "
            "No authorities or strangers were contacted."
        )
    )


@router.get("/{event_id}/status", response_model=SosEventOut)
def get_sos_status(
    event_id: str,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Polls the status of an active SOS alert."""
    event = db.query(SosEvent).filter(
        SosEvent.id == event_id,
        SosEvent.user_id == current_user.id
    ).first()

    if not event:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="SOS event not found."
        )

    return SosEventOut(
        id=event.id,
        user_id=event.user_id,
        latitude=event.latitude,
        longitude=event.longitude,
        accuracy=event.accuracy,
        is_last_known=event.is_last_known,
        trigger_type=event.trigger_type,
        status=event.status,
        delivery_attempts=event.delivery_attempts,
        recipients_count=event.recipients_count,
        delivered_count=event.delivered_count,
        message_body=event.message_body,
        triggered_at=event.triggered_at,
        recipients=[],
        is_idempotent_replay=False
    )
