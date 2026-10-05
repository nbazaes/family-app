from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
from typing import Optional, List
from datetime import datetime, timezone
import uuid

from app.core.database import get_db
from app.models.family import User
from app.models.item import Item
from app.schemas.item import (
    ItemCreate,
    ItemUpdate,
    ItemOut,
    BatchSyncItemsRequest,
    BatchSyncItemsResponse,
)
from app.api.deps import get_current_user
from app.services.sync_service import SyncService
from app.core.sse import sse_hub

router = APIRouter(prefix="/items", tags=["Items"])


@router.get("", response_model=List[ItemOut])
async def list_items(
    type: Optional[str] = Query(None, pattern="^(SHOPPING|TASK)$"),
    include_completed: bool = Query(True),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    query = select(Item).where(
        and_(
            Item.family_id == current_user.family_id,
            Item.deleted_at.is_(None),
        )
    )

    if type:
        query = query.where(Item.type == type)
    if not include_completed:
        query = query.where(Item.is_completed == False)

    query = query.order_by(Item.created_at.desc())
    res = await db.execute(query)
    return res.scalars().all()


@router.post("", response_model=ItemOut, status_code=status.HTTP_201_CREATED)
async def create_item(
    payload: ItemCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    now = datetime.now(timezone.utc)
    item_id = payload.id or str(uuid.uuid4())
    
    item = Item(
        id=item_id,
        family_id=current_user.family_id,
        type=payload.type,
        title=payload.title,
        is_completed=payload.is_completed,
        completed_at=payload.completed_at,
        quantity=payload.quantity,
        category=payload.category,
        assigned_to=payload.assigned_to,
        due_date=payload.due_date,
        created_at=payload.created_at or now,
        updated_at=payload.updated_at or now,
    )
    db.add(item)
    await db.commit()
    await db.refresh(item)

    await sse_hub.broadcast(
        current_user.family_id,
        "item_created",
        {
            "id": item.id,
            "type": item.type,
            "title": item.title,
            "updated_at": item.updated_at.isoformat(),
        },
    )

    return item


@router.post("/batch-sync", response_model=BatchSyncItemsResponse)
async def batch_sync(
    payload: BatchSyncItemsRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    synced_ids, server_items = await SyncService.sync_items(
        db=db,
        family_id=current_user.family_id,
        incoming_items=payload.items,
        since=payload.since,
    )

    return BatchSyncItemsResponse(
        synced_ids=synced_ids,
        server_items=[ItemOut.model_validate(item) for item in server_items],
    )


@router.patch("/{item_id}", response_model=ItemOut)
async def update_item(
    item_id: str,
    payload: ItemUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(Item).where(
        and_(
            Item.id == item_id,
            Item.family_id == current_user.family_id,
            Item.deleted_at.is_(None),
        )
    )
    res = await db.execute(stmt)
    item = res.scalar_one_or_none()
    if not item:
        raise HTTPException(status_code=404, detail="Item no encontrado")

    update_data = payload.model_dump(exclude_unset=True)
    for field, val in update_data.items():
        setattr(item, field, val)

    item.updated_at = payload.updated_at or datetime.now(timezone.utc)
    if payload.is_completed is not None:
        item.completed_at = datetime.now(timezone.utc) if payload.is_completed else None

    await db.commit()
    await db.refresh(item)

    await sse_hub.broadcast(
        current_user.family_id,
        "item_updated",
        {
            "id": item.id,
            "type": item.type,
            "title": item.title,
            "is_completed": item.is_completed,
            "updated_at": item.updated_at.isoformat(),
        },
    )

    return item


@router.delete("/{item_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_item(
    item_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(Item).where(
        and_(
            Item.id == item_id,
            Item.family_id == current_user.family_id,
            Item.deleted_at.is_(None),
        )
    )
    res = await db.execute(stmt)
    item = res.scalar_one_or_none()
    if not item:
        raise HTTPException(status_code=404, detail="Item no encontrado")

    # Soft delete so offline clients can sync the deletion
    now = datetime.now(timezone.utc)
    item.deleted_at = now
    item.updated_at = now
    await db.commit()

    await sse_hub.broadcast(
        current_user.family_id,
        "item_deleted",
        {"id": item.id, "type": item.type, "updated_at": item.updated_at.isoformat()},
    )
    return None
