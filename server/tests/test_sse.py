import pytest
import asyncio
from app.core.sse import sse_hub


@pytest.mark.asyncio
async def test_sse_hub_broadcast():
    family_id = "test-family-uuid-1234"
    queue = await sse_hub.subscribe(family_id)

    try:
        # Broadcast an event
        event_payload = {"id": "item-123", "title": "Pan integral", "type": "SHOPPING"}
        await sse_hub.broadcast(family_id, "item_created", event_payload)

        # Receive from queue
        msg = await asyncio.wait_for(queue.get(), timeout=2.0)
        assert msg["type"] == "item_created"
        assert msg["data"]["title"] == "Pan integral"
        assert msg["data"]["id"] == "item-123"
    finally:
        await sse_hub.unsubscribe(family_id, queue)
