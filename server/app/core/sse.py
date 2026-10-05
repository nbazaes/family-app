import asyncio
import json
import logging
from typing import Dict, Set

logger = logging.getLogger(__name__)


class SSEHub:
    def __init__(self):
        # family_id -> set of asyncio.Queue
        self._subscribers: Dict[str, Set[asyncio.Queue]] = {}
        self._lock = asyncio.Lock()

    async def subscribe(self, family_id: str) -> asyncio.Queue:
        queue = asyncio.Queue()
        async with self._lock:
            if family_id not in self._subscribers:
                self._subscribers[family_id] = set()
            self._subscribers[family_id].add(queue)
        logger.info(f"SSE client subscribed to family {family_id}. Active: {len(self._subscribers[family_id])}")
        return queue

    async def unsubscribe(self, family_id: str, queue: asyncio.Queue):
        async with self._lock:
            if family_id in self._subscribers:
                self._subscribers[family_id].discard(queue)
                if not self._subscribers[family_id]:
                    del self._subscribers[family_id]
        logger.info(f"SSE client unsubscribed from family {family_id}")

    async def broadcast(self, family_id: str, event_type: str, data: dict):
        message = {
            "type": event_type,
            "data": data,
        }
        async with self._lock:
            queues = list(self._subscribers.get(family_id, set()))

        for q in queues:
            try:
                q.put_nowait(message)
            except Exception as e:
                logger.error(f"Failed to put message to queue: {e}")


sse_hub = SSEHub()
