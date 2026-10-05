import pytest
from httpx import AsyncClient
from datetime import datetime, timezone, timedelta
import uuid


@pytest.mark.asyncio
async def test_finance_accounts_and_transactions(client: AsyncClient, auth_headers: dict):
    headers = {"Authorization": auth_headers["Authorization"]}

    # 1. Create two accounts: "Efectivo" ($50000) and "Banco" ($200000)
    acc1_payload = {
        "name": "Efectivo",
        "initial_balance": 50000.0,
        "color_hex": "#1E523A",
    }
    r1 = await client.post("/api/finance/accounts", json=acc1_payload, headers=headers)
    assert r1.status_code == 201, r1.text
    acc1 = r1.json()
    assert acc1["name"] == "Efectivo"
    assert acc1["current_balance"] == 50000.0

    acc2_payload = {
        "name": "Banco",
        "initial_balance": 200000.0,
        "color_hex": "#1976D2",
    }
    r2 = await client.post("/api/finance/accounts", json=acc2_payload, headers=headers)
    assert r2.status_code == 201, r2.text
    acc2 = r2.json()

    # 2. Add an expense of $12000 to "Efectivo" (Supermercado)
    tx1_payload = {
        "account_id": acc1["id"],
        "amount": 12000.0,
        "category": "Supermercado",
        "description": "Verdulería y pan",
        "date": datetime.now(timezone.utc).isoformat(),
        "type": "EXPENSE",
        "created_by": "TestUser",
    }
    tx_res = await client.post("/api/finance/transactions", json=tx1_payload, headers=headers)
    assert tx_res.status_code == 201, tx_res.text
    tx1 = tx_res.json()
    assert tx1["amount"] == 12000.0

    # 3. Add an expense of $45000 to "Banco" (Servicios)
    tx2_payload = {
        "account_id": acc2["id"],
        "amount": 45000.0,
        "category": "Servicios",
        "description": "Luz y Agua",
        "date": datetime.now(timezone.utc).isoformat(),
        "type": "EXPENSE",
        "created_by": "TestUser",
    }
    tx2_res = await client.post("/api/finance/transactions", json=tx2_payload, headers=headers)
    assert tx2_res.status_code == 201, tx2_res.text

    # 4. Check list_accounts balances
    # acc1: 50000 - 12000 = 38000
    # acc2: 200000 - 45000 = 155000
    acc_list_res = await client.get("/api/finance/accounts", headers=headers)
    assert acc_list_res.status_code == 200
    acc_list = acc_list_res.json()
    acc1_found = next(a for a in acc_list if a["id"] == acc1["id"])
    acc2_found = next(a for a in acc_list if a["id"] == acc2["id"])
    assert acc1_found["current_balance"] == 38000.0
    assert acc2_found["current_balance"] == 155000.0

    # 5. Check Finance Summary
    # general_balance: 38000 + 155000 = 193000
    # total_expenses_month: 12000 + 45000 = 57000
    summary_res = await client.get("/api/finance/summary", headers=headers)
    assert summary_res.status_code == 200
    summary = summary_res.json()
    assert summary["general_balance"] == 193000.0
    assert summary["total_expenses_month"] == 57000.0


@pytest.mark.asyncio
async def test_finance_batch_sync(client: AsyncClient, auth_headers: dict):
    headers = {"Authorization": auth_headers["Authorization"]}

    acc_id = str(uuid.uuid4())
    tx_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc)

    payload = {
        "accounts": [
            {
                "id": acc_id,
                "name": "Billetera",
                "initial_balance": 15000.0,
                "color_hex": "#9E4726",
                "created_at": now.isoformat(),
                "updated_at": now.isoformat(),
            }
        ],
        "transactions": [
            {
                "id": tx_id,
                "account_id": acc_id,
                "amount": 3500.0,
                "category": "Transporte",
                "description": "Bencina",
                "date": now.isoformat(),
                "type": "EXPENSE",
                "created_by": "Familiar",
                "created_at": now.isoformat(),
                "updated_at": now.isoformat(),
            }
        ],
        "since": None,
    }

    sync_res = await client.post("/api/finance/batch-sync", json=payload, headers=headers)
    assert sync_res.status_code == 200, sync_res.text
    data = sync_res.json()
    assert acc_id in data["synced_account_ids"]
    assert tx_id in data["synced_transaction_ids"]
    assert any(a["id"] == acc_id for a in data["server_accounts"])
    assert any(t["id"] == tx_id for t in data["server_transactions"])
