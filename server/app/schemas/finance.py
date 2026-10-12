from pydantic import BaseModel, Field, ConfigDict, field_validator
from typing import Optional, List
from datetime import datetime
from app.schemas.common import empty_str_to_none, parse_tolerant_datetime


class AccountBase(BaseModel):
    name: str
    initial_balance: float = 0.0
    color_hex: str = "#1E523A"


class AccountCreate(AccountBase):
    id: Optional[str] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    @field_validator("created_at", "updated_at", mode="before")
    @classmethod
    def parse_optional_dt(cls, v):
        return parse_tolerant_datetime(v)


class AccountUpdate(BaseModel):
    name: Optional[str] = None
    initial_balance: Optional[float] = None
    color_hex: Optional[str] = None
    updated_at: Optional[datetime] = None

    @field_validator("updated_at", mode="before")
    @classmethod
    def parse_optional_dt(cls, v):
        return parse_tolerant_datetime(v)


class AccountOut(AccountBase):
    model_config = ConfigDict(from_attributes=True)

    id: str
    family_id: str
    current_balance: Optional[float] = None
    created_at: datetime
    updated_at: datetime
    deleted_at: Optional[datetime] = None


class AccountSyncPayload(AccountBase):
    id: str
    created_at: datetime
    updated_at: datetime
    deleted_at: Optional[datetime] = None

    @field_validator("created_at", "updated_at", "deleted_at", mode="before")
    @classmethod
    def parse_sync_dt(cls, v):
        return parse_tolerant_datetime(v)


class FinanceTransactionBase(BaseModel):
    account_id: str
    amount: float
    category: str
    description: str = ""
    date: datetime
    type: str = Field(default="EXPENSE", pattern="^(EXPENSE|INCOME)$")
    created_by: str

    @field_validator("date", mode="before")
    @classmethod
    def parse_tx_date(cls, v):
        return parse_tolerant_datetime(v)

    @field_validator("description", mode="before")
    @classmethod
    def clean_tx_desc(cls, v):
        return v or ""


class FinanceTransactionCreate(FinanceTransactionBase):
    id: Optional[str] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    @field_validator("created_at", "updated_at", mode="before")
    @classmethod
    def parse_optional_dt(cls, v):
        return parse_tolerant_datetime(v)


class FinanceTransactionUpdate(BaseModel):
    account_id: Optional[str] = None
    amount: Optional[float] = None
    category: Optional[str] = None
    description: Optional[str] = None
    date: Optional[datetime] = None
    type: Optional[str] = Field(default=None, pattern="^(EXPENSE|INCOME)$")
    updated_at: Optional[datetime] = None

    @field_validator("date", "updated_at", mode="before")
    @classmethod
    def parse_optional_dt(cls, v):
        return parse_tolerant_datetime(v)


class FinanceTransactionOut(FinanceTransactionBase):
    model_config = ConfigDict(from_attributes=True)

    id: str
    family_id: str
    created_at: datetime
    updated_at: datetime
    deleted_at: Optional[datetime] = None


class FinanceTransactionSyncPayload(FinanceTransactionBase):
    id: str
    created_at: datetime
    updated_at: datetime
    deleted_at: Optional[datetime] = None

    @field_validator("created_at", "updated_at", "deleted_at", mode="before")
    @classmethod
    def parse_sync_dt(cls, v):
        return parse_tolerant_datetime(v)


class BatchSyncFinanceRequest(BaseModel):
    accounts: List[AccountSyncPayload] = []
    transactions: List[FinanceTransactionSyncPayload] = []
    since: Optional[datetime] = None

    @field_validator("since", mode="before")
    @classmethod
    def parse_since(cls, v):
        return parse_tolerant_datetime(v)


class BatchSyncFinanceResponse(BaseModel):
    synced_account_ids: List[str]
    server_accounts: List[AccountOut]
    synced_transaction_ids: List[str]
    server_transactions: List[FinanceTransactionOut]


class FinanceSummaryOut(BaseModel):
    general_balance: float
    total_expenses_month: float
    accounts: List[AccountOut]

