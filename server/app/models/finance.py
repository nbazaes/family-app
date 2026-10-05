import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Float, DateTime, ForeignKey, Index, Text
from sqlalchemy.orm import relationship
from app.core.database import Base


def get_utc_now():
    return datetime.now(timezone.utc)


class Account(Base):
    __tablename__ = "accounts"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    family_id = Column(String(36), ForeignKey("families.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    initial_balance = Column(Float, default=0.0, nullable=False)
    color_hex = Column(String(20), default="#1E523A", nullable=False)

    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

    family = relationship("Family", back_populates="accounts")
    transactions = relationship("FinanceTransaction", back_populates="account", cascade="all, delete-orphan")

    __table_args__ = (
        Index("idx_accounts_family_id", "family_id"),
        Index("idx_accounts_updated_at", "updated_at"),
    )


class FinanceTransaction(Base):
    __tablename__ = "finance_transactions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    family_id = Column(String(36), ForeignKey("families.id", ondelete="CASCADE"), nullable=False, index=True)
    account_id = Column(String(36), ForeignKey("accounts.id", ondelete="CASCADE"), nullable=False, index=True)
    amount = Column(Float, nullable=False)
    category = Column(String(100), nullable=False)  # e.g., 'Supermercado', 'Servicios', 'Transporte'
    description = Column(String(255), nullable=False)
    date = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    type = Column(String(20), default="EXPENSE", nullable=False)  # 'EXPENSE' | 'INCOME'
    created_by = Column(String(100), nullable=False)

    created_at = Column(DateTime(timezone=True), default=get_utc_now, nullable=False)
    updated_at = Column(DateTime(timezone=True), default=get_utc_now, onupdate=get_utc_now, nullable=False)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

    family = relationship("Family", back_populates="transactions")
    account = relationship("Account", back_populates="transactions")

    __table_args__ = (
        Index("idx_finance_tx_family_date", "family_id", "date"),
        Index("idx_finance_tx_account_id", "account_id"),
        Index("idx_finance_tx_updated_at", "updated_at"),
    )
