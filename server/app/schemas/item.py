from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List
from datetime import datetime


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


class ItemCreate(ItemBase):
    id: Optional[str] = None  # Client-generated UUIDv4 allowed
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None


class ItemUpdate(BaseModel):
    title: Optional[str] = None
    is_completed: Optional[bool] = None
    completed_at: Optional[datetime] = None
    quantity: Optional[str] = None
    category: Optional[str] = None
    assigned_to: Optional[str] = None
    due_date: Optional[datetime] = None
    updated_at: Optional[datetime] = None


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


class BatchSyncItemsRequest(BaseModel):
    items: List[ItemSyncPayload] = []
    since: Optional[datetime] = None


class BatchSyncItemsResponse(BaseModel):
    synced_ids: List[str]
    server_items: List[ItemOut]
