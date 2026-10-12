from pydantic import BaseModel, Field, ConfigDict, field_validator
from typing import Optional, List
from datetime import datetime
from app.schemas.common import empty_str_to_none, parse_tolerant_datetime


class ItemBase(BaseModel):
    title: str
    type: str = Field(..., pattern="^(SHOPPING|TASK)$")
    is_completed: bool = False
    completed_at: Optional[datetime] = None
    
    # Shopping
    quantity: Optional[str] = None
    category: Optional[str] = None
    
    # Task
    assigned_to: Optional[str] = None
    due_date: Optional[datetime] = None

    @field_validator("completed_at", "due_date", mode="before")
    @classmethod
    def parse_optional_dt(cls, v):
        return parse_tolerant_datetime(v)

    @field_validator("quantity", "category", "assigned_to", mode="before")
    @classmethod
    def clean_optional_str(cls, v):
        return empty_str_to_none(v)


class ItemCreate(ItemBase):
    id: Optional[str] = None  # Client-generated UUIDv4 allowed
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    @field_validator("created_at", "updated_at", mode="before")
    @classmethod
    def parse_meta_dt(cls, v):
        return parse_tolerant_datetime(v)


class ItemUpdate(BaseModel):
    title: Optional[str] = None
    is_completed: Optional[bool] = None
    completed_at: Optional[datetime] = None
    quantity: Optional[str] = None
    category: Optional[str] = None
    assigned_to: Optional[str] = None
    due_date: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    @field_validator("completed_at", "due_date", "updated_at", mode="before")
    @classmethod
    def parse_update_dt(cls, v):
        return parse_tolerant_datetime(v)

    @field_validator("quantity", "category", "assigned_to", mode="before")
    @classmethod
    def clean_update_str(cls, v):
        return empty_str_to_none(v)


class ItemOut(ItemBase):
    model_config = ConfigDict(from_attributes=True)

    id: str
    family_id: str
    created_at: datetime
    updated_at: datetime
    deleted_at: Optional[datetime] = None


class ItemSyncPayload(ItemBase):
    id: str
    created_at: datetime
    updated_at: datetime
    deleted_at: Optional[datetime] = None

    @field_validator("created_at", "updated_at", "deleted_at", mode="before")
    @classmethod
    def parse_sync_dt(cls, v):
        return parse_tolerant_datetime(v)


class BatchSyncItemsRequest(BaseModel):
    items: List[ItemSyncPayload] = []
    since: Optional[datetime] = None

    @field_validator("since", mode="before")
    @classmethod
    def parse_since(cls, v):
        return parse_tolerant_datetime(v)


class BatchSyncItemsResponse(BaseModel):
    synced_ids: List[str]
    server_items: List[ItemOut]

