from pydantic import BaseModel, EmailStr, ConfigDict
from typing import Optional
from datetime import datetime


class ConnectRequest(BaseModel):
    member_name: Optional[str] = "Familiar"
    family_code: Optional[str] = None


class UserRegister(BaseModel):
    name: str
    email: EmailStr
    password: str
    family_name: Optional[str] = "Familia"
    access_code: Optional[str] = None


class FamilyCreate(BaseModel):
    name: str
    access_code: Optional[str] = None


class UserLogin(BaseModel):
    email: EmailStr
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: str
    family_id: str
    name: str
    email: Optional[str] = None


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    family_id: str
    name: str
    email: Optional[str] = None
    role: str
    created_at: datetime
