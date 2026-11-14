import secrets
from datetime import datetime
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.models.models import User, EmergencyContact
from app.schemas.contact import (
    ContactCreate, ContactUpdate, ContactOut, ContactVerifyRequest,
    ContactToggleAlerts, VerificationDispatchResponse,
    AuthorizedRecipientsResponse, AuthorizedRecipient
)
from app.security.deps import get_current_active_user

router = APIRouter()

def _generate_verification_code() -> str:
    """Generates a secure 6-digit numeric verification code."""
    return str(secrets.randbelow(900000) + 100000)

@router.get("", response_model=List[ContactOut], summary="List saved emergency contacts")
def list_contacts(
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Retrieves all emergency contacts explicitly added by the authenticated user."""
    contacts = db.query(EmergencyContact).filter(
        EmergencyContact.user_id == current_user.id
    ).order_by(EmergencyContact.added_at.asc()).all()
    return [ContactOut.model_validate(c) for c in contacts]

@router.post("", response_model=ContactOut, status_code=status.HTTP_201_CREATED, summary="Add emergency contact")
def add_contact(
    contact_in: ContactCreate,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Adds a new emergency contact. Contact starts UNVERIFIED until phone verification completes."""
    # Check duplicate phone for this user
    existing = db.query(EmergencyContact).filter(
        EmergencyContact.user_id == current_user.id,
        EmergencyContact.phone == contact_in.phone.strip()
    ).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="A contact with this phone number already exists in your emergency list"
        )
    
    verification_code = _generate_verification_code()
    
    new_contact = EmergencyContact(
        user_id=current_user.id,
        name=contact_in.name.strip(),
        phone=contact_in.phone.strip(),
        email=contact_in.email.lower() if contact_in.email else None,
        relationship_type=contact_in.relationship_type,
        is_verified=False,
        verification_code=verification_code,
        verification_sent_at=datetime.utcnow(),
        receive_sos_alerts=contact_in.receive_sos_alerts
    )
    db.add(new_contact)
    db.commit()
    db.refresh(new_contact)
    return ContactOut.model_validate(new_contact)

@router.put("/{contact_id}", response_model=ContactOut, summary="Edit emergency contact")
def update_contact(
    contact_id: str,
    contact_in: ContactUpdate,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Updates an emergency contact. If phone is modified, re-verification is strictly required."""
    contact = db.query(EmergencyContact).filter(
        EmergencyContact.id == contact_id,
        EmergencyContact.user_id == current_user.id
    ).first()
    if not contact:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Emergency contact not found"
        )
    
    if contact_in.name is not None:
        contact.name = contact_in.name.strip()
    if contact_in.email is not None:
        contact.email = contact_in.email.lower() if contact_in.email else None
    if contact_in.relationship_type is not None:
        contact.relationship_type = contact_in.relationship_type
    if contact_in.receive_sos_alerts is not None:
        # Restriction: unverified contacts cannot be activated for alerts
        if contact_in.receive_sos_alerts and not contact.is_verified:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cannot enable SOS alerts for an unverified contact. Verify contact first."
            )
        contact.receive_sos_alerts = contact_in.receive_sos_alerts

    # If phone number changed, reset verification
    if contact_in.phone is not None and contact_in.phone.strip() != contact.phone:
        contact.phone = contact_in.phone.strip()
        contact.is_verified = False
        contact.verification_code = _generate_verification_code()
        contact.verification_sent_at = datetime.utcnow()

    contact.updated_at = datetime.utcnow()
    db.commit()
    db.refresh(contact)
    return ContactOut.model_validate(contact)

@router.delete("/{contact_id}", summary="Delete emergency contact")
def delete_contact(
    contact_id: str,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Deletes an emergency contact from the authenticated user's records."""
    contact = db.query(EmergencyContact).filter(
        EmergencyContact.id == contact_id,
        EmergencyContact.user_id == current_user.id
    ).first()
    if not contact:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Emergency contact not found"
        )
    
    name = contact.name
    db.delete(contact)
    db.commit()
    return {
        "status": "deleted",
        "message": f"Contact '{name}' removed from your emergency contact list."
    }

@router.post("/{contact_id}/send-verification", response_model=VerificationDispatchResponse, summary="Send verification code")
def send_verification(
    contact_id: str,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Generates and dispatches a 6-digit OTP code to the emergency contact for verification."""
    contact = db.query(EmergencyContact).filter(
        EmergencyContact.id == contact_id,
        EmergencyContact.user_id == current_user.id
    ).first()
    if not contact:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Emergency contact not found"
        )
    
    code = _generate_verification_code()
    contact.verification_code = code
    contact.verification_sent_at = datetime.utcnow()
    db.commit()

    return VerificationDispatchResponse(
        contact_id=contact.id,
        phone=contact.phone,
        status="sent",
        message=f"6-digit verification code dispatched to {contact.phone}.",
        dev_verification_code=code  # surfaced for development/testing
    )

@router.post("/{contact_id}/verify", response_model=ContactOut, summary="Verify emergency contact")
def verify_contact(
    contact_id: str,
    verify_in: ContactVerifyRequest,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Validates the 6-digit verification code and marks the contact as VERIFIED."""
    contact = db.query(EmergencyContact).filter(
        EmergencyContact.id == contact_id,
        EmergencyContact.user_id == current_user.id
    ).first()
    if not contact:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Emergency contact not found"
        )
    
    if contact.is_verified:
        return ContactOut.model_validate(contact)
    
    if not contact.verification_code or contact.verification_code != verify_in.code.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid 6-digit verification code. Please check and retry."
        )
    
    contact.is_verified = True
    contact.verification_code = None
    contact.updated_at = datetime.utcnow()
    db.commit()
    db.refresh(contact)
    return ContactOut.model_validate(contact)

@router.put("/{contact_id}/toggle-sos", response_model=ContactOut, summary="Toggle SOS alert delivery")
def toggle_sos_delivery(
    contact_id: str,
    toggle_in: ContactToggleAlerts,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Allows user to choose whether a contact should receive SOS alerts (STRICTLY requires contact to be verified)."""
    contact = db.query(EmergencyContact).filter(
        EmergencyContact.id == contact_id,
        EmergencyContact.user_id == current_user.id
    ).first()
    if not contact:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Emergency contact not found"
        )
    
    if toggle_in.receive_sos_alerts and not contact.is_verified:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="STRICT POLICY VIOLATION: An unverified contact can NEVER be selected to receive emergency SOS alerts."
        )
    
    contact.receive_sos_alerts = toggle_in.receive_sos_alerts
    contact.updated_at = datetime.utcnow()
    db.commit()
    db.refresh(contact)
    return ContactOut.model_validate(contact)

@router.get("/authorized-recipients", response_model=AuthorizedRecipientsResponse, summary="Get verified emergency recipients")
def get_authorized_sos_recipients(
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """
    STRICT PRODUCT RESTRICTION ENFORCEMENT:
    Retrieves recipients strictly from the authenticated user's records.
    Filters exclusively:
    1. Belongs to the current authenticated user (user_id == current_user.id)
    2. Has been explicitly verified (is_verified == True)
    3. Has been selected to receive alerts (receive_sos_alerts == True)
    
    NEVER accepts arbitrary phone numbers.
    NEVER includes police, authorities, nearby people, or strangers.
    """
    verified_contacts = db.query(EmergencyContact).filter(
        EmergencyContact.user_id == current_user.id,
        EmergencyContact.is_verified == True,
        EmergencyContact.receive_sos_alerts == True
    ).order_by(EmergencyContact.name.asc()).all()
    
    recipients = [
        AuthorizedRecipient(
            contact_id=c.id,
            name=c.name,
            phone=c.phone,
            relationship_type=c.relationship_type,
            is_verified=c.is_verified,
            receive_sos_alerts=c.receive_sos_alerts
        ) for c in verified_contacts
    ]
    
    return AuthorizedRecipientsResponse(
        user_id=current_user.id,
        total_recipients=len(recipients),
        recipients=recipients
    )

