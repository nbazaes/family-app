import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.core.database import get_db
from app.core.security import hash_password, verify_password, create_access_token
from app.models.family import Family, User
from app.schemas.auth import UserRegister, UserLogin, TokenResponse, UserOut, FamilyCreate, ConnectRequest
from app.core.config import settings
from app.api.deps import get_current_user

router = APIRouter(prefix="/auth", tags=["Auth"])


@router.post("/connect", response_model=TokenResponse)
async def connect_family(payload: ConnectRequest, db: AsyncSession = Depends(get_db)):
    family = None
    if payload.family_code:
        # Search by access code or ID
        stmt = select(Family).where(
            (Family.access_code == payload.family_code.strip()) | (Family.id == payload.family_code.strip())
        )
        res = await db.execute(stmt)
        family = res.scalar_one_or_none()
        if not family:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"No se encontró ninguna familia con el código '{payload.family_code}'",
            )
    else:
        # Default single-tenant family: get first family or create default
        stmt = select(Family).order_by(Family.created_at.asc())
        res = await db.execute(stmt)
        family = res.scalar_one_or_none()
        if not family:
            family = Family(
                name=settings.DEFAULT_FAMILY_NAME,
                access_code=settings.DEFAULT_FAMILY_CODE,
            )
            db.add(family)
            await db.flush()

    member_name = (payload.member_name or "Familiar").strip()
    user = User(
        family_id=family.id,
        name=member_name,
        email=f"member_{uuid.uuid4().hex[:12]}@family.local",
        hashed_password="",
        role="member",
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)

    token = create_access_token({"sub": user.id, "family_id": family.id})
    return TokenResponse(
        access_token=token,
        token_type="bearer",
        user_id=user.id,
        family_id=family.id,
        name=user.name,
        email=None,
    )


@router.post("/register", response_model=TokenResponse)
async def register(payload: UserRegister, db: AsyncSession = Depends(get_db)):
    # Check if email exists
    stmt = select(User).where(User.email == payload.email)
    res = await db.execute(stmt)
    if res.scalar_one_or_none():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Ya existe una cuenta registrada con este correo electrónico",
        )

    # Create new family
    family = Family(name=payload.family_name or "Familia")
    db.add(family)
    await db.flush()

    user = User(
        family_id=family.id,
        name=payload.name,
        email=payload.email,
        hashed_password=hash_password(payload.password),
        role="admin",
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)

    token = create_access_token({"sub": user.id, "family_id": family.id})
    return TokenResponse(
        access_token=token,
        token_type="bearer",
        user_id=user.id,
        family_id=family.id,
        name=user.name,
        email=user.email,
    )


@router.post("/login", response_model=TokenResponse)
async def login(payload: UserLogin, db: AsyncSession = Depends(get_db)):
    stmt = select(User).where(User.email == payload.email)
    res = await db.execute(stmt)
    user = res.scalar_one_or_none()

    if not user or not verify_password(payload.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales incorrectas",
        )

    token = create_access_token({"sub": user.id, "family_id": user.family_id})
    return TokenResponse(
        access_token=token,
        token_type="bearer",
        user_id=user.id,
        family_id=user.family_id,
        name=user.name,
        email=user.email,
    )


@router.get("/me", response_model=UserOut)
async def get_me(current_user: User = Depends(get_current_user)):
    return current_user
