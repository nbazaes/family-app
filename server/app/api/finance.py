import uuid
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select, and_, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.core.database import get_db
from app.core.sse import sse_hub
from app.models.family import User
from app.models.finance import Account, FinanceTransaction
from app.schemas.finance import (
    AccountCreate,
    AccountUpdate,
    AccountOut,
    FinanceTransactionCreate,
    FinanceTransactionUpdate,
    FinanceTransactionOut,
    BatchSyncFinanceRequest,
    BatchSyncFinanceResponse,
    FinanceSummaryOut,
)
from app.services.sync_service import SyncService

router = APIRouter(prefix="/finance", tags=["Finance"])


async def compute_account_balance(db: AsyncSession, family_id: str, account_id: str, initial_balance: float) -> float:
    # Sum expenses and incomes for this account
    stmt = select(
        FinanceTransaction.type,
        func.sum(FinanceTransaction.amount).label("total")
    ).where(
        and_(
            FinanceTransaction.family_id == family_id,
            FinanceTransaction.account_id == account_id,
            FinanceTransaction.deleted_at.is_(None),
        )
    ).group_by(FinanceTransaction.type)
    
    res = await db.execute(stmt)
    rows = res.all()
    
    expenses = 0.0
    incomes = 0.0
    for row in rows:
        if row.type == "EXPENSE":
            expenses = float(row.total or 0.0)
        elif row.type == "INCOME":
            incomes = float(row.total or 0.0)

    return round(initial_balance - expenses + incomes, 2)


@router.get("/accounts", response_model=List[AccountOut])
async def list_accounts(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    query = select(Account).where(
        and_(
            Account.family_id == current_user.family_id,
            Account.deleted_at.is_(None),
        )
    ).order_by(Account.created_at.asc())
    
    res = await db.execute(query)
    accounts = res.scalars().all()
    
    out_accounts = []
    for acc in accounts:
        curr_bal = await compute_account_balance(db, current_user.family_id, acc.id, acc.initial_balance)
        acc_dict = {
            "id": acc.id,
            "family_id": acc.family_id,
            "name": acc.name,
            "initial_balance": acc.initial_balance,
            "color_hex": acc.color_hex,
            "current_balance": curr_bal,
            "created_at": acc.created_at,
            "updated_at": acc.updated_at,
            "deleted_at": acc.deleted_at,
        }
        out_accounts.append(AccountOut(**acc_dict))

    return out_accounts


@router.post("/accounts", response_model=AccountOut, status_code=status.HTTP_201_CREATED)
async def create_account(
    payload: AccountCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    now = datetime.now(timezone.utc)
    acc_id = payload.id or str(uuid.uuid4())

    account = Account(
        id=acc_id,
        family_id=current_user.family_id,
        name=payload.name,
        initial_balance=payload.initial_balance,
        color_hex=payload.color_hex,
        created_at=payload.created_at or now,
        updated_at=payload.updated_at or now,
    )
    db.add(account)
    await db.commit()
    await db.refresh(account)

    await sse_hub.broadcast(
        current_user.family_id,
        "account_created",
        {
            "id": account.id,
            "name": account.name,
            "updated_at": account.updated_at.isoformat(),
        },
    )

    return AccountOut(
        id=account.id,
        family_id=account.family_id,
        name=account.name,
        initial_balance=account.initial_balance,
        color_hex=account.color_hex,
        current_balance=account.initial_balance,
        created_at=account.created_at,
        updated_at=account.updated_at,
        deleted_at=account.deleted_at,
    )


@router.put("/accounts/{account_id}", response_model=AccountOut)
async def update_account(
    account_id: str,
    payload: AccountUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(Account).where(
        and_(
            Account.id == account_id,
            Account.family_id == current_user.family_id,
            Account.deleted_at.is_(None),
        )
    )
    res = await db.execute(stmt)
    account = res.scalar_one_or_none()
    if not account:
        raise HTTPException(status_code=404, detail="Cuenta no encontrada")

    if payload.name is not None:
        account.name = payload.name
    if payload.initial_balance is not None:
        account.initial_balance = payload.initial_balance
    if payload.color_hex is not None:
        account.color_hex = payload.color_hex
    account.updated_at = payload.updated_at or datetime.now(timezone.utc)

    await db.commit()
    await db.refresh(account)

    curr_bal = await compute_account_balance(db, current_user.family_id, account.id, account.initial_balance)

    await sse_hub.broadcast(
        current_user.family_id,
        "account_updated",
        {
            "id": account.id,
            "name": account.name,
            "updated_at": account.updated_at.isoformat(),
        },
    )

    return AccountOut(
        id=account.id,
        family_id=account.family_id,
        name=account.name,
        initial_balance=account.initial_balance,
        color_hex=account.color_hex,
        current_balance=curr_bal,
        created_at=account.created_at,
        updated_at=account.updated_at,
        deleted_at=account.deleted_at,
    )


@router.delete("/accounts/{account_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_account(
    account_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(Account).where(
        and_(
            Account.id == account_id,
            Account.family_id == current_user.family_id,
            Account.deleted_at.is_(None),
        )
    )
    res = await db.execute(stmt)
    account = res.scalar_one_or_none()
    if not account:
        raise HTTPException(status_code=404, detail="Cuenta no encontrada")

    now = datetime.now(timezone.utc)
    account.deleted_at = now
    account.updated_at = now
    await db.commit()

    await sse_hub.broadcast(
        current_user.family_id,
        "account_deleted",
        {
            "id": account.id,
            "name": account.name,
            "updated_at": account.updated_at.isoformat(),
        },
    )


@router.get("/transactions", response_model=List[FinanceTransactionOut])
async def list_transactions(
    account_id: Optional[str] = Query(None),
    category: Optional[str] = Query(None),
    type: Optional[str] = Query(None, pattern="^(EXPENSE|INCOME)$"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    query = select(FinanceTransaction).where(
        and_(
            FinanceTransaction.family_id == current_user.family_id,
            FinanceTransaction.deleted_at.is_(None),
        )
    )

    if account_id:
        query = query.where(FinanceTransaction.account_id == account_id)
    if category:
        query = query.where(FinanceTransaction.category == category)
    if type:
        query = query.where(FinanceTransaction.type == type)

    query = query.order_by(FinanceTransaction.date.desc())
    res = await db.execute(query)
    return res.scalars().all()


@router.post("/transactions", response_model=FinanceTransactionOut, status_code=status.HTTP_201_CREATED)
async def create_transaction(
    payload: FinanceTransactionCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # Verify account exists
    acc_stmt = select(Account).where(
        and_(
            Account.id == payload.account_id,
            Account.family_id == current_user.family_id,
            Account.deleted_at.is_(None),
        )
    )
    acc_res = await db.execute(acc_stmt)
    if not acc_res.scalar_one_or_none():
        raise HTTPException(status_code=404, detail="Cuenta seleccionada no existe")

    now = datetime.now(timezone.utc)
    tx_id = payload.id or str(uuid.uuid4())

    tx = FinanceTransaction(
        id=tx_id,
        family_id=current_user.family_id,
        account_id=payload.account_id,
        amount=payload.amount,
        category=payload.category,
        description=payload.description,
        date=payload.date,
        type=payload.type,
        created_by=payload.created_by,
        created_at=payload.created_at or now,
        updated_at=payload.updated_at or now,
    )
    db.add(tx)
    await db.commit()
    await db.refresh(tx)

    await sse_hub.broadcast(
        current_user.family_id,
        "finance_transaction_created",
        {
            "id": tx.id,
            "account_id": tx.account_id,
            "amount": tx.amount,
            "category": tx.category,
            "description": tx.description,
            "updated_at": tx.updated_at.isoformat(),
        },
    )

    return tx


@router.delete("/transactions/{transaction_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_transaction(
    transaction_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(FinanceTransaction).where(
        and_(
            FinanceTransaction.id == transaction_id,
            FinanceTransaction.family_id == current_user.family_id,
            FinanceTransaction.deleted_at.is_(None),
        )
    )
    res = await db.execute(stmt)
    tx = res.scalar_one_or_none()
    if not tx:
        raise HTTPException(status_code=404, detail="Transacción no encontrada")

    now = datetime.now(timezone.utc)
    tx.deleted_at = now
    tx.updated_at = now
    await db.commit()

    await sse_hub.broadcast(
        current_user.family_id,
        "finance_transaction_deleted",
        {
            "id": tx.id,
            "account_id": tx.account_id,
            "amount": tx.amount,
            "updated_at": tx.updated_at.isoformat(),
        },
    )


@router.get("/summary", response_model=FinanceSummaryOut)
async def get_summary(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # Fetch accounts
    acc_stmt = select(Account).where(
        and_(
            Account.family_id == current_user.family_id,
            Account.deleted_at.is_(None),
        )
    ).order_by(Account.created_at.asc())
    acc_res = await db.execute(acc_stmt)
    accounts = acc_res.scalars().all()

    general_balance = 0.0
    out_accounts = []
    for acc in accounts:
        curr_bal = await compute_account_balance(db, current_user.family_id, acc.id, acc.initial_balance)
        general_balance += curr_bal
        out_accounts.append(AccountOut(
            id=acc.id,
            family_id=acc.family_id,
            name=acc.name,
            initial_balance=acc.initial_balance,
            color_hex=acc.color_hex,
            current_balance=curr_bal,
            created_at=acc.created_at,
            updated_at=acc.updated_at,
            deleted_at=acc.deleted_at,
        ))

    # Calculate current month expenses
    now = datetime.now(timezone.utc)
    start_of_month = datetime(now.year, now.month, 1, tzinfo=timezone.utc)
    stmt_month = select(func.sum(FinanceTransaction.amount)).where(
        and_(
            FinanceTransaction.family_id == current_user.family_id,
            FinanceTransaction.type == "EXPENSE",
            FinanceTransaction.date >= start_of_month,
            FinanceTransaction.deleted_at.is_(None),
        )
    )
    res_month = await db.execute(stmt_month)
    total_month = float(res_month.scalar() or 0.0)

    return FinanceSummaryOut(
        general_balance=round(general_balance, 2),
        total_expenses_month=round(total_month, 2),
        accounts=out_accounts,
    )


@router.post("/batch-sync", response_model=BatchSyncFinanceResponse)
async def batch_sync(
    payload: BatchSyncFinanceRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    synced_account_ids, server_accounts = await SyncService.sync_accounts(
        db=db,
        family_id=current_user.family_id,
        incoming_accounts=payload.accounts,
        since=payload.since,
    )

    synced_tx_ids, server_txs = await SyncService.sync_finance_transactions(
        db=db,
        family_id=current_user.family_id,
        incoming_transactions=payload.transactions,
        since=payload.since,
    )

    # Compute current balances for server accounts
    out_accounts = []
    for acc in server_accounts:
        curr_bal = await compute_account_balance(db, current_user.family_id, acc.id, acc.initial_balance)
        out_accounts.append(AccountOut(
            id=acc.id,
            family_id=acc.family_id,
            name=acc.name,
            initial_balance=acc.initial_balance,
            color_hex=acc.color_hex,
            current_balance=curr_bal,
            created_at=acc.created_at,
            updated_at=acc.updated_at,
            deleted_at=acc.deleted_at,
        ))

    return BatchSyncFinanceResponse(
        synced_account_ids=synced_account_ids,
        server_accounts=out_accounts,
        synced_transaction_ids=synced_tx_ids,
        server_transactions=[FinanceTransactionOut.model_validate(tx) for tx in server_txs],
    )
