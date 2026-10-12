from pydantic import BaseModel, ConfigDict, field_validator
from typing import Optional, List
from datetime import datetime
from app.schemas.common import empty_str_to_none, parse_tolerant_datetime


class CalendarEventBase(BaseModel):
    title: str
    description: Optional[str] = None
    start_time: datetime
    end_time: datetime
    is_all_day: bool = False
    color_hex: str = "#4285F4"
    created_by: str

    @field_validator("description", mode="before")
    @classmethod
    def clean_description(cls, v):
        return empty_str_to_none(v)

    @field_validator("start_time", "end_time", mode="before")
    @classmethod
    def parse_datetimes(cls, v):
        return parse_tolerant_datetime(v)


class CalendarEventCreate(CalendarEventBase):
    id: Optional[str] = None  # Client generated UUIDv4
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    @field_validator("created_at", "updated_at", mode="before")
    @classmethod
    def parse_optional_dt(cls, v):
        return parse_tolerant_datetime(v)


class CalendarEventUpdate(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    is_all_day: Optional[bool] = None
    color_hex: Optional[str] = None
    updated_at: Optional[datetime] = None

    @field_validator("description", mode="before")
    @classmethod
    def clean_desc(cls, v):
        return empty_str_to_none(v)

    @field_validator("start_time", "end_time", "updated_at", mode="before")
    @classmethod
    def parse_optional_dt(cls, v):
        return parse_tolerant_datetime(v)


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

    @field_validator("created_at", "updated_at", "deleted_at", mode="before")
    @classmethod
    def parse_sync_dt(cls, v):
        return parse_tolerant_datetime(v)


class BatchSyncEventsRequest(BaseModel):
    events: List[CalendarEventSyncPayload] = []
    since: Optional[datetime] = None

    @field_validator("since", mode="before")
    @classmethod
    def parse_since(cls, v):
        return parse_tolerant_datetime(v)


class BatchSyncEventsResponse(BaseModel):
    synced_ids: List[str]
    server_events: List[CalendarEventOut]

