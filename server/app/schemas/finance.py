from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List
from datetime import datetime


class AccountBase(BaseModel):
    name: str
    initial_balance: float = 0.0
    color_hex: str = "#1E523A"


class AccountCreate(AccountBase):
    id: Optional[str] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None


class AccountUpdate(BaseModel):
    name: Optional[str] = None
    initial_balance: Optional[float] = None
    color_hex: Optional[str] = None
    updated_at: Optional[datetime] = None


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


class FinanceTransactionBase(BaseModel):
    account_id: str
    amount: float
    category: str
    description: str
    date: datetime
    type: str = Field(default="EXPENSE", pattern="^(EXPENSE|INCOME)$")
    created_by: str


class FinanceTransactionCreate(FinanceTransactionBase):
    id: Optional[str] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None


class FinanceTransactionUpdate(BaseModel):
    account_id: Optional[str] = None
    amount: Optional[float] = None
    category: Optional[str] = None
    description: Optional[str] = None
    date: Optional[datetime] = None
    type: Optional[str] = Field(default=None, pattern="^(EXPENSE|INCOME)$")
    updated_at: Optional[datetime] = None


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


class BatchSyncFinanceRequest(BaseModel):
    accounts: List[AccountSyncPayload] = []
    transactions: List[FinanceTransactionSyncPayload] = []
    since: Optional[datetime] = None


class BatchSyncFinanceResponse(BaseModel):
    synced_account_ids: List[str]
    server_accounts: List[AccountOut]
    synced_transaction_ids: List[str]
    server_transactions: List[FinanceTransactionOut]


class FinanceSummaryOut(BaseModel):
    general_balance: float
    total_expenses_month: float
    accounts: List[AccountOut]
