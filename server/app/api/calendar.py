from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
from typing import Optional, List
from datetime import datetime, timezone
import uuid

from app.core.database import get_db
from app.models.family import User, Family
from app.models.event import CalendarEvent
from app.schemas.event import (
    CalendarEventCreate,
    CalendarEventUpdate,
    CalendarEventOut,
    BatchSyncEventsRequest,
    BatchSyncEventsResponse,
)
from app.api.deps import get_current_user, get_user_from_token_param
from app.services.sync_service import SyncService, ensure_tz_aware
from app.services.ical_service import ICalService
from app.core.sse import sse_hub

router = APIRouter(prefix="/calendar", tags=["Calendar"])


@router.get("/events", response_model=List[CalendarEventOut])
async def list_events(
    start: Optional[datetime] = Query(None),
    end: Optional[datetime] = Query(None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    query = select(CalendarEvent).where(
        and_(
            CalendarEvent.family_id == current_user.family_id,
            CalendarEvent.deleted_at.is_(None),
        )
    )

    if start:
        query = query.where(CalendarEvent.end_time >= ensure_tz_aware(start))
    if end:
        query = query.where(CalendarEvent.start_time <= ensure_tz_aware(end))

    query = query.order_by(CalendarEvent.start_time.asc())
    res = await db.execute(query)
    return res.scalars().all()


@router.post("/events", response_model=CalendarEventOut, status_code=status.HTTP_201_CREATED)
async def create_event(
    payload: CalendarEventCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    now = datetime.now(timezone.utc)
    ev_id = payload.id or str(uuid.uuid4())

    event = CalendarEvent(
        id=ev_id,
        family_id=current_user.family_id,
        title=payload.title,
        description=payload.description,
        start_time=ensure_tz_aware(payload.start_time),
        end_time=ensure_tz_aware(payload.end_time),
        is_all_day=payload.is_all_day,
        color_hex=payload.color_hex,
        created_by=payload.created_by or current_user.name,
        created_at=payload.created_at or now,
        updated_at=payload.updated_at or now,
    )
    db.add(event)
    await db.commit()
    await db.refresh(event)

    await sse_hub.broadcast(
        current_user.family_id,
        "event_created",
        {
            "id": event.id,
            "title": event.title,
            "start_time": event.start_time.isoformat(),
            "end_time": event.end_time.isoformat(),
            "updated_at": event.updated_at.isoformat(),
        },
    )

    return event


@router.post("/events/batch-sync", response_model=BatchSyncEventsResponse)
async def batch_sync_events(
    payload: BatchSyncEventsRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    synced_ids, server_events = await SyncService.sync_events(
        db=db,
        family_id=current_user.family_id,
        incoming_events=payload.events,
        since=payload.since,
    )

    return BatchSyncEventsResponse(
        synced_ids=synced_ids,
        server_events=[CalendarEventOut.model_validate(ev) for ev in server_events],
    )


@router.patch("/events/{event_id}", response_model=CalendarEventOut)
async def update_event(
    event_id: str,
    payload: CalendarEventUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(CalendarEvent).where(
        and_(
            CalendarEvent.id == event_id,
            CalendarEvent.family_id == current_user.family_id,
            CalendarEvent.deleted_at.is_(None),
        )
    )
    res = await db.execute(stmt)
    event = res.scalar_one_or_none()
    if not event:
        raise HTTPException(status_code=404, detail="Evento no encontrado")

    update_data = payload.model_dump(exclude_unset=True)
    for field, val in update_data.items():
        if field in ("start_time", "end_time") and val is not None:
            val = ensure_tz_aware(val)
        setattr(event, field, val)

    event.updated_at = payload.updated_at or datetime.now(timezone.utc)
    await db.commit()
    await db.refresh(event)

    await sse_hub.broadcast(
        current_user.family_id,
        "event_updated",
        {
            "id": event.id,
            "title": event.title,
            "start_time": event.start_time.isoformat(),
            "end_time": event.end_time.isoformat(),
            "updated_at": event.updated_at.isoformat(),
        },
    )

    return event


@router.delete("/events/{event_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_event(
    event_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(CalendarEvent).where(
        and_(
            CalendarEvent.id == event_id,
            CalendarEvent.family_id == current_user.family_id,
            CalendarEvent.deleted_at.is_(None),
        )
    )
    res = await db.execute(stmt)
    event = res.scalar_one_or_none()
    if not event:
        raise HTTPException(status_code=404, detail="Evento no encontrado")

    now = datetime.now(timezone.utc)
    event.deleted_at = now
    event.updated_at = now
    await db.commit()

    await sse_hub.broadcast(
        current_user.family_id,
        "event_deleted",
        {"id": event.id, "title": event.title, "updated_at": event.updated_at.isoformat()},
    )
    return None


@router.get("/feed.ics")
async def get_calendar_feed(
    user: User = Depends(get_user_from_token_param),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(CalendarEvent).where(
        and_(
            CalendarEvent.family_id == user.family_id,
            CalendarEvent.deleted_at.is_(None),
        )
    ).order_by(CalendarEvent.start_time.asc())
    res = await db.execute(stmt)
    events = list(res.scalars().all())

    # Fetch family name
    fam_stmt = select(Family).where(Family.id == user.family_id)
    fam_res = await db.execute(fam_stmt)
    family = fam_res.scalar_one_or_none()
    family_name = family.name if family else "Familia"

    ical_content = ICalService.generate_calendar_feed(
        events=events,
        cal_name=f"Calendario - {family_name}",
    )

    return Response(
        content=ical_content,
        media_type="text/calendar; charset=utf-8",
        headers={
            "Content-Disposition": 'inline; filename="calendar.ics"',
            "Cache-Control": "no-cache, no-store, must-revalidate",
        },
    )


@router.get("/export.ics")
async def export_calendar(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(CalendarEvent).where(
        and_(
            CalendarEvent.family_id == user.family_id,
            CalendarEvent.deleted_at.is_(None),
        )
    ).order_by(CalendarEvent.start_time.asc())
    res = await db.execute(stmt)
    events = list(res.scalars().all())

    fam_stmt = select(Family).where(Family.id == user.family_id)
    fam_res = await db.execute(fam_stmt)
    family = fam_res.scalar_one_or_none()
    family_name = family.name if family else "Familia"

    ical_content = ICalService.generate_calendar_feed(
        events=events,
        cal_name=f"Calendario - {family_name}",
    )

    return Response(
        content=ical_content,
        media_type="text/calendar; charset=utf-8",
        headers={
            "Content-Disposition": f'attachment; filename="family-calendar.ics"',
        },
    )
