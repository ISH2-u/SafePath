from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field, ConfigDict

EMAIL_REGEX = r"^[^@\s]+@[^@\s]+\.[^@\s]+$"


class UserRegister(BaseModel):
    email: str = Field(..., pattern=EMAIL_REGEX, description="Valid email address")
    password: str = Field(..., min_length=8, description="Password must be at least 8 characters")
    display_name: str = Field(..., min_length=1, description="Full name")
    phone: Optional[str] = None


class UserLogin(BaseModel):
    email: str = Field(..., pattern=EMAIL_REGEX, description="Valid email address")
    password: str


class UserUpdate(BaseModel):
    display_name: Optional[str] = None
    phone: Optional[str] = None
    blood_group: Optional[str] = None
    medical_notes: Optional[str] = None


class UserDeleteConfirm(BaseModel):
    confirm: bool = Field(..., description="Must be true to permanently delete account")
    password: str = Field(..., description="Confirmation password to prevent accidental deletion")


class UserOut(BaseModel):
    id: str
    email: str
    display_name: Optional[str] = None
    phone: Optional[str] = None
    blood_group: Optional[str] = None
    medical_notes: Optional[str] = None
    is_active: bool
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)

