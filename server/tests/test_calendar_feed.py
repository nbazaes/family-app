import pytest
from httpx import AsyncClient
from datetime import datetime, timezone, timedelta
import uuid


@pytest.mark.asyncio
async def test_calendar_event_and_feed(client: AsyncClient, auth_headers: dict):
    headers = {"Authorization": auth_headers["Authorization"]}
    token = auth_headers["token"]

    start = datetime.now(timezone.utc) + timedelta(days=1)
    end = start + timedelta(hours=2)

    ev_id = str(uuid.uuid4())
    create_res = await client.post(
        "/api/calendar/events",
        json={
            "id": ev_id,
            "title": "Cena Familiar de Cumpleaños",
            "description": "Restaurante italiano del centro",
            "start_time": start.isoformat(),
            "end_time": end.isoformat(),
            "is_all_day": False,
            "color_hex": "#E91E63",
            "created_by": "Nicolas",
        },
        headers=headers,
    )
    assert create_res.status_code == 201, create_res.text
    event_data = create_res.json()
    assert event_data["title"] == "Cena Familiar de Cumpleaños"

    # Test feed.ics via query parameter token (?token=...)
    feed_res = await client.get(f"/api/calendar/feed.ics?token={token}")
    assert feed_res.status_code == 200
    assert "text/calendar" in feed_res.headers["content-type"]
    ics_text = feed_res.text

    # Validate RFC 5545 compliance
    assert "BEGIN:VCALENDAR" in ics_text
    assert "VERSION:2.0" in ics_text
    assert "PRODID:-//FamilyApp//Family Calendar 1.0//ES" in ics_text
    assert "BEGIN:VEVENT" in ics_text
    assert f"UID:{ev_id}@familyapp" in ics_text
    assert "SUMMARY:Cena Familiar de Cumpleaños" in ics_text
    assert "DESCRIPTION:Restaurante italiano del centro" in ics_text
    assert "END:VEVENT" in ics_text
    assert "END:VCALENDAR" in ics_text

    # Test export.ics via Bearer header
    export_res = await client.get("/api/calendar/export.ics", headers=headers)
    assert export_res.status_code == 200
    assert "attachment" in export_res.headers["content-disposition"]
