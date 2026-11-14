import uuid
from datetime import datetime
from sqlalchemy import Column, String, Boolean, DateTime, Float, ForeignKey, Text, Integer
from sqlalchemy.orm import relationship
from app.database.base import Base


class User(Base):
    __tablename__ = "users"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    email = Column(String, unique=True, nullable=False, index=True)
    hashed_password = Column(String, nullable=False)
    display_name = Column(String, nullable=True)
    phone = Column(String, nullable=True)
    blood_group = Column(String, nullable=True)
    medical_notes = Column(Text, nullable=True)
    firebase_uid = Column(String, unique=True, nullable=True, index=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Cascades ensure complete account deletion when requested
    contacts = relationship("EmergencyContact", back_populates="owner", cascade="all, delete-orphan")
    journeys = relationship("Journey", back_populates="owner", cascade="all, delete-orphan")
    sos_events = relationship("SosEvent", back_populates="owner", cascade="all, delete-orphan")


class EmergencyContact(Base):
    __tablename__ = "emergency_contacts"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    name = Column(String, nullable=False)
    phone = Column(String, nullable=False)
    email = Column(String, nullable=True)
    relationship_type = Column(String, nullable=True)  # e.g., Parent, Spouse, Sibling, Friend
    is_verified = Column(Boolean, default=False)
    verification_code = Column(String, nullable=True)  # 6-digit verification code
    verification_sent_at = Column(DateTime, nullable=True)
    receive_sos_alerts = Column(Boolean, default=True)  # User choice: should this contact receive SOS alerts?
    added_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    owner = relationship("User", back_populates="contacts")


class Journey(Base):
    __tablename__ = "journeys"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    destination_label = Column(String, nullable=True)
    start_lat = Column(Float, nullable=True)
    start_lng = Column(Float, nullable=True)
    status = Column(String, default="ACTIVE")  # ACTIVE | COMPLETED | CANCELLED
    started_at = Column(DateTime, default=datetime.utcnow)
    ended_at = Column(DateTime, nullable=True)

    owner = relationship("User", back_populates="journeys")


class SosEvent(Base):
    __tablename__ = "sos_events"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    accuracy = Column(Float, nullable=True)
    is_last_known = Column(Boolean, default=False)
    idempotency_key = Column(String, unique=True, index=True, nullable=True)
    trigger_type = Column(String, default="MANUAL")  # MANUAL | VOICE | FALL_DETECT
    status = Column(String, default="PENDING")  # PENDING | SENT | DELIVERED | FAILED
    delivery_attempts = Column(Integer, default=1)
    recipients_count = Column(Integer, default=0)
    delivered_count = Column(Integer, default=0)
    message_body = Column(Text, nullable=True)
    triggered_at = Column(DateTime, default=datetime.utcnow)
    notes = Column(Text, nullable=True)

    owner = relationship("User", back_populates="sos_events")
