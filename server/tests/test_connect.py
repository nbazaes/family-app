import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_zero_account_connect(client: AsyncClient):
    # 1. Connect without credentials to default family
    resp = await client.post(
        "/api/auth/connect",
        json={"member_name": "Papá Nicolás"}
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert "access_token" in data
    assert data["name"] == "Papá Nicolás"
    assert "family_id" in data

    token = data["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # 2. Sync items directly with the token returned by connect
    item_payload = {
        "items": [
            {
                "id": "zero-account-item-1",
                "type": "SHOPPING",
                "title": "Manzanas rojas",
                "category": "Frutas",
                "created_at": "2026-10-02T16:00:00Z",
                "updated_at": "2026-10-02T16:00:00Z"
            }
        ]
    }
    sync_resp = await client.post("/api/items/batch-sync", json=item_payload, headers=headers)
    assert sync_resp.status_code == 200, sync_resp.text
    sync_data = sync_resp.json()
    assert "zero-account-item-1" in sync_data["synced_ids"]
