from pydantic import BaseModel, ConfigDict
from typing import Optional, List
from datetime import datetime


class CalendarEventBase(BaseModel):
    title: str
    description: Optional[str] = None
    start_time: datetime
    end_time: datetime
    is_all_day: bool = False
    color_hex: str = "#4285F4"
    created_by: str


class CalendarEventCreate(CalendarEventBase):
    id: Optional[str] = None  # Client generated UUIDv4
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None


class CalendarEventUpdate(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    is_all_day: Optional[bool] = None
    color_hex: Optional[str] = None
    updated_at: Optional[datetime] = None


class CalendarEventOut(CalendarEventBase):
    model_config = ConfigDict(from_attributes=True)

    id: str
    family_id: str
    created_at: datetime
    updated_at: datetime
    deleted_at: Optional[datetime] = None


class CalendarEventSyncPayload(CalendarEventBase):
    id: str
    created_at: datetime
    updated_at: datetime
    deleted_at: Optional[datetime] = None


class BatchSyncEventsRequest(BaseModel):
    events: List[CalendarEventSyncPayload] = []
    since: Optional[datetime] = None


class BatchSyncEventsResponse(BaseModel):
    synced_ids: List[str]
    server_events: List[CalendarEventOut]
