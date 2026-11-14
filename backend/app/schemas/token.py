from typing import Optional
from pydantic import BaseModel
from app.schemas.user import UserOut


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


class TokenData(BaseModel):
    user_id: Optional[str] = None

