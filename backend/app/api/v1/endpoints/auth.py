from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from app.database.session import get_db
from app.models.models import User
from app.schemas.user import UserRegister, UserLogin, UserUpdate, UserDeleteConfirm, UserOut
from app.schemas.token import Token
from app.security.security import get_password_hash, verify_password, create_access_token
from app.security.deps import get_current_active_user

router = APIRouter()

@router.post("/register", response_model=Token, status_code=status.HTTP_201_CREATED, summary="Register new user")
def register(user_in: UserRegister, db: Session = Depends(get_db)):
    """Registers a new user account with hashed password and returns an authentication token."""
    existing_user = db.query(User).filter(User.email == user_in.email.lower()).first()
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="An account with this email address already exists"
        )
    
    # Securely hash password — NEVER store plaintext
    hashed_pwd = get_password_hash(user_in.password)
    
    new_user = User(
        email=user_in.email.lower(),
        hashed_password=hashed_pwd,
        display_name=user_in.display_name,
        phone=user_in.phone,
        is_active=True
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    
    access_token = create_access_token(subject=new_user.id)
    return Token(
        access_token=access_token,
        token_type="bearer",
        user=UserOut.model_validate(new_user)
    )

@router.post("/login", response_model=Token, summary="User authentication")
def login(login_in: UserLogin, db: Session = Depends(get_db)):
    """Authenticates credentials against the stored cryptographic hash and issues a JWT token."""
    user = db.query(User).filter(User.email == login_in.email.lower()).first()
    if not user or not verify_password(login_in.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="User account is deactivated"
        )
    
    access_token = create_access_token(subject=user.id)
    return Token(
        access_token=access_token,
        token_type="bearer",
        user=UserOut.model_validate(user)
    )

@router.post("/logout", summary="Logout user")
def logout(current_user: User = Depends(get_current_active_user)):
    """Logs out the user. The client must discard the stored Bearer token."""
    return {
        "status": "success",
        "message": f"User {current_user.email} successfully logged out. Discard client token."
    }

@router.get("/me", response_model=UserOut, summary="Get current user profile")
def get_profile(current_user: User = Depends(get_current_active_user)):
    """Retrieves the profile of the currently authenticated user."""
    return UserOut.model_validate(current_user)

@router.put("/me", response_model=UserOut, summary="Update user profile")
def update_profile(
    update_in: UserUpdate,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Updates user profile details (display name, phone, blood group, medical notes)."""
    if update_in.display_name is not None:
        current_user.display_name = update_in.display_name
    if update_in.phone is not None:
        current_user.phone = update_in.phone
    if update_in.blood_group is not None:
        current_user.blood_group = update_in.blood_group
    if update_in.medical_notes is not None:
        current_user.medical_notes = update_in.medical_notes
    
    current_user.updated_at = datetime.utcnow()
    db.commit()
    db.refresh(current_user)
    return UserOut.model_validate(current_user)

@router.delete("/me", summary="Delete user account")
def delete_account(
    delete_in: UserDeleteConfirm,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db)
):
    """Permanently deletes the user account and cascades to delete all contacts, journeys and logs."""
    if not delete_in.confirm:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Account deletion requires explicit confirmation (confirm: true)"
        )
    
    # Require password verification to prevent unauthorized deletion
    if not verify_password(delete_in.password, current_user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect confirmation password. Deletion aborted."
        )
    
    email = current_user.email
    db.delete(current_user)
    db.commit()
    return {
        "status": "deleted",
        "message": f"Account for {email} and all associated data have been permanently deleted."
    }

