import asyncio
import json
from fastapi import APIRouter, Depends
from starlette.responses import StreamingResponse
from app.models.family import User
from app.api.deps import get_user_from_token_param
from app.core.sse import sse_hub

router = APIRouter(prefix="/events", tags=["Realtime"])


@router.get("/stream")
async def event_stream(user: User = Depends(get_user_from_token_param)):
    family_id = user.family_id
    queue = await sse_hub.subscribe(family_id)

    async def event_generator():
        try:
            # Send initial connected event
            init_payload = json.dumps({"status": "connected", "family_id": family_id})
            yield f"event: connected\ndata: {init_payload}\n\n"

            while True:
                try:
                    # Wait for next event or send ping every 15 seconds
                    msg = await asyncio.wait_for(queue.get(), timeout=15.0)
                    event_type = msg.get("type", "message")
                    data_json = json.dumps(msg.get("data", {}))
                    yield f"event: {event_type}\ndata: {data_json}\n\n"
                except asyncio.TimeoutError:
                    # Keep-alive heartbeat
                    yield ": ping\n\n"
        except asyncio.CancelledError:
            pass
        finally:
            await sse_hub.unsubscribe(family_id, queue)

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
