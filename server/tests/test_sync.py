import pytest
from httpx import AsyncClient
from datetime import datetime, timezone, timedelta
import uuid


@pytest.mark.asyncio
async def test_batch_sync_items_and_lww(client: AsyncClient, auth_headers: dict):
    headers = {"Authorization": auth_headers["Authorization"]}
    item_id = str(uuid.uuid4())
    t1 = datetime.now(timezone.utc) - timedelta(minutes=10)
    t2 = datetime.now(timezone.utc)

    # 1. Sync new item from client (offline creation)
    payload = {
        "items": [
            {
                "id": item_id,
                "type": "SHOPPING",
                "title": "Leche deslactosada",
                "category": "Lácteos",
                "quantity": "2 litros",
                "is_completed": False,
                "created_at": t1.isoformat(),
                "updated_at": t1.isoformat(),
            }
        ],
        "since": None,
    }

    res = await client.post("/api/items/batch-sync", json=payload, headers=headers)
    assert res.status_code == 200, res.text
    data = res.json()
    assert item_id in data["synced_ids"]
    assert any(i["id"] == item_id and i["title"] == "Leche deslactosada" for i in data["server_items"])

    # 2. Server update via PATCH (e.g. spouse checked it off)
    patch_res = await client.patch(
        f"/api/items/{item_id}",
        json={"is_completed": True, "updated_at": t2.isoformat()},
        headers=headers,
    )
    assert patch_res.status_code == 200
    assert patch_res.json()["is_completed"] is True

    # 3. Client tries to sync an outdated state (updated_at = t1 < t2)
    stale_payload = {
        "items": [
            {
                "id": item_id,
                "type": "SHOPPING",
                "title": "Leche deslactosada (stale)",
                "category": "Lácteos",
                "is_completed": False,
                "created_at": t1.isoformat(),
                "updated_at": t1.isoformat(),  # Outdated!
            }
        ],
        "since": (t1 + timedelta(seconds=1)).isoformat(),
    }
    sync_res = await client.post("/api/items/batch-sync", json=stale_payload, headers=headers)
    assert sync_res.status_code == 200
    sync_data = sync_res.json()
    # 4. Test soft delete sync
    t3 = datetime.now(timezone.utc)
    del_payload = {
        "items": [
            {
                "id": item_id,
                "type": "SHOPPING",
                "title": "Leche deslactosada",
                "is_completed": True,
                "created_at": t1.isoformat(),
                "updated_at": t3.isoformat(),
                "deleted_at": t3.isoformat(),
            }
        ],
        "since": None,
    }
    del_res = await client.post("/api/items/batch-sync", json=del_payload, headers=headers)
    assert del_res.status_code == 200
    del_data = del_res.json()
    assert item_id in del_data["synced_ids"]
    del_item = next(i for i in del_data["server_items"] if i["id"] == item_id)
    assert del_item["deleted_at"] is not None


@pytest.mark.asyncio
async def test_batch_sync_calendar_events(client: AsyncClient, auth_headers: dict):
    headers = {"Authorization": auth_headers["Authorization"]}
    ev_id = str(uuid.uuid4())
    t1 = datetime.now(timezone.utc)
    start = t1 + timedelta(days=2)
    end = start + timedelta(hours=1)

    payload = {
        "events": [
            {
                "id": ev_id,
                "title": "Reunión de Padres y Maestros",
                "description": "Aula 3B",
                "start_time": start.isoformat(),
                "end_time": end.isoformat(),
                "is_all_day": False,
                "color_hex": "#4CAF50",
                "created_by": "Nicolas",
                "created_at": t1.isoformat(),
                "updated_at": t1.isoformat(),
            }
        ],
        "since": None,
    }

    res = await client.post("/api/calendar/events/batch-sync", json=payload, headers=headers)
    assert res.status_code == 200, res.text
    data = res.json()
    assert ev_id in data["synced_ids"]
    assert any(e["id"] == ev_id and e["title"] == "Reunión de Padres y Maestros" for e in data["server_events"])

