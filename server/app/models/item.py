import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Boolean, DateTime, ForeignKey, Index
from sqlalchemy.orm import relationship
from app.core.database import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class Item(Base):
    __tablename__ = "items"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    family_id = Column(String(36), ForeignKey("families.id", ondelete="CASCADE"), nullable=False, index=True)
    type = Column(String(20), nullable=False)  # 'SHOPPING' | 'TASK'
    title = Column(String(255), nullable=False)
    is_completed = Column(Boolean, default=False, nullable=False)
    completed_at = Column(DateTime(timezone=True), nullable=True)
    
    # Shopping specific fields
    quantity = Column(String(100), nullable=True)
    category = Column(String(100), nullable=True)  # e.g., 'Lácteos', 'Limpieza'

    # Task specific fields
    assigned_to = Column(String(100), nullable=True)
    due_date = Column(DateTime(timezone=True), nullable=True)

    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)
    deleted_at = Column(DateTime(timezone=True), nullable=True)  # Soft delete for sync

    family = relationship("Family", back_populates="items")

    __table_args__ = (
        Index("idx_items_family_type", "family_id", "type"),
        Index("idx_items_updated_at", "updated_at"),
    )
