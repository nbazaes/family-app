from typing import List, Tuple, Optional
from datetime import datetime
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, or_
from app.models.item import Item
from app.models.event import CalendarEvent
from app.schemas.item import ItemSyncPayload, ItemOut
from app.schemas.event import CalendarEventSyncPayload, CalendarEventOut
from app.core.sse import sse_hub


def ensure_tz_aware(dt: Optional[datetime]) -> Optional[datetime]:
    if dt is None:
        return None
    if dt.tzinfo is None:
        from datetime import timezone
        return dt.replace(tzinfo=timezone.utc)
    return dt


class SyncService:
    @staticmethod
    async def sync_items(
        db: AsyncSession,
        family_id: str,
        incoming_items: List[ItemSyncPayload],
        since: Optional[datetime] = None,
    ) -> Tuple[List[str], List[Item]]:
        synced_ids = []
        now_dt = datetime.now()

        for item_data in incoming_items:
            client_updated_at = ensure_tz_aware(item_data.updated_at)
            
            stmt = select(Item).where(
                and_(Item.id == item_data.id, Item.family_id == family_id)
            )
            res = await db.execute(stmt)
            existing: Optional[Item] = res.scalar_one_or_none()

            if existing is None:
                # Insert new item
                new_item = Item(
                    id=item_data.id,
                    family_id=family_id,
                    type=item_data.type,
                    title=item_data.title,
                    is_completed=item_data.is_completed,
                    completed_at=item_data.completed_at,
                    quantity=item_data.quantity,
                    category=item_data.category,
                    assigned_to=item_data.assigned_to,
                    due_date=item_data.due_date,
                    created_at=item_data.created_at,
                    updated_at=item_data.updated_at,
                    deleted_at=item_data.deleted_at,
                )
                db.add(new_item)
                synced_ids.append(item_data.id)
                await sse_hub.broadcast(
                    family_id,
                    "item_created",
                    {
                        "id": new_item.id,
                        "type": new_item.type,
                        "title": new_item.title,
                        "updated_at": new_item.updated_at.isoformat(),
                    },
                )
            else:
                server_updated_at = ensure_tz_aware(existing.updated_at)
                # Last-Write-Wins: Client wins if updated_at >= server updated_at
                if client_updated_at >= server_updated_at:
                    existing.type = item_data.type
                    existing.title = item_data.title
                    existing.is_completed = item_data.is_completed
                    existing.completed_at = item_data.completed_at
                    existing.quantity = item_data.quantity
                    existing.category = item_data.category
                    existing.assigned_to = item_data.assigned_to
                    existing.due_date = item_data.due_date
                    existing.updated_at = item_data.updated_at
                    existing.deleted_at = item_data.deleted_at
                    synced_ids.append(item_data.id)
                    await sse_hub.broadcast(
                        family_id,
                        "item_updated" if not item_data.deleted_at else "item_deleted",
                        {
                            "id": existing.id,
                            "type": existing.type,
                            "title": existing.title,
                            "updated_at": existing.updated_at.isoformat(),
                        },
                    )
                else:
                    # Server version is newer; client will receive the server version in response
                    synced_ids.append(item_data.id)

        await db.commit()

        # Query all items modified on server since `since`
        query = select(Item).where(Item.family_id == family_id)
        if since is not None:
            since_aware = ensure_tz_aware(since)
            query = query.where(Item.updated_at > since_aware)
        
        query = query.order_by(Item.updated_at.asc())
        server_items_res = await db.execute(query)
        server_items = list(server_items_res.scalars().all())

        return synced_ids, server_items

    @staticmethod
    async def sync_events(
        db: AsyncSession,
        family_id: str,
        incoming_events: List[CalendarEventSyncPayload],
        since: Optional[datetime] = None,
    ) -> Tuple[List[str], List[CalendarEvent]]:
        synced_ids = []

        for ev_data in incoming_events:
            client_updated_at = ensure_tz_aware(ev_data.updated_at)

            stmt = select(CalendarEvent).where(
                and_(CalendarEvent.id == ev_data.id, CalendarEvent.family_id == family_id)
            )
            res = await db.execute(stmt)
            existing: Optional[CalendarEvent] = res.scalar_one_or_none()

            if existing is None:
                new_ev = CalendarEvent(
                    id=ev_data.id,
                    family_id=family_id,
                    title=ev_data.title,
                    description=ev_data.description,
                    start_time=ev_data.start_time,
                    end_time=ev_data.end_time,
                    is_all_day=ev_data.is_all_day,
                    color_hex=ev_data.color_hex,
                    created_by=ev_data.created_by,
                    created_at=ev_data.created_at,
                    updated_at=ev_data.updated_at,
                    deleted_at=ev_data.deleted_at,
                )
                db.add(new_ev)
                synced_ids.append(ev_data.id)
                await sse_hub.broadcast(
                    family_id,
                    "event_created",
                    {
                        "id": new_ev.id,
                        "title": new_ev.title,
                        "start_time": new_ev.start_time.isoformat(),
                        "end_time": new_ev.end_time.isoformat(),
                        "updated_at": new_ev.updated_at.isoformat(),
                    },
                )
            else:
                server_updated_at = ensure_tz_aware(existing.updated_at)
                if client_updated_at >= server_updated_at:
                    existing.title = ev_data.title
                    existing.description = ev_data.description
                    existing.start_time = ev_data.start_time
                    existing.end_time = ev_data.end_time
                    existing.is_all_day = ev_data.is_all_day
                    existing.color_hex = ev_data.color_hex
                    existing.created_by = ev_data.created_by
                    existing.updated_at = ev_data.updated_at
                    existing.deleted_at = ev_data.deleted_at
                    synced_ids.append(ev_data.id)
                    await sse_hub.broadcast(
                        family_id,
                        "event_updated" if not ev_data.deleted_at else "event_deleted",
                        {
                            "id": existing.id,
                            "title": existing.title,
                            "updated_at": existing.updated_at.isoformat(),
                        },
                    )
                else:
                    synced_ids.append(ev_data.id)

        await db.commit()

        query = select(CalendarEvent).where(CalendarEvent.family_id == family_id)
        if since is not None:
            since_aware = ensure_tz_aware(since)
            query = query.where(CalendarEvent.updated_at > since_aware)
        
        query = query.order_by(CalendarEvent.updated_at.asc())
        server_events_res = await db.execute(query)
        server_events = list(server_events_res.scalars().all())

        return synced_ids, server_events
