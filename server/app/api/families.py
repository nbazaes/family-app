from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.core.database import get_db
from app.models.family import Family, User
from app.schemas.auth import FamilyCreate
from app.api.deps import get_current_user

router = APIRouter(prefix="/families", tags=["Families"])


@router.post("", status_code=status.HTTP_201_CREATED)
async def create_family(
    payload: FamilyCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    family = Family(name=payload.name)
    db.add(family)
    await db.flush()

    # Move current user to new family
    current_user.family_id = family.id
    current_user.role = "admin"
    await db.commit()

    return {"id": family.id, "name": family.name}


@router.get("/current")
async def get_current_family(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(Family).where(Family.id == current_user.family_id)
    res = await db.execute(stmt)
    family = res.scalar_one_or_none()
    if not family:
        raise HTTPException(status_code=404, detail="Familia no encontrada")
    
    return {"id": family.id, "name": family.name, "created_at": family.created_at}
