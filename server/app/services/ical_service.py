from typing import List
from datetime import datetime, timezone
from app.models.event import CalendarEvent


def escape_ical_text(text: str) -> str:
    if not text:
        return ""
    text = text.replace("\\", "\\\\")
    text = text.replace(";", "\\;")
    text = text.replace(",", "\\,")
    text = text.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
    return text


def format_dt(dt: datetime, is_all_day: bool = False) -> str:
    # Ensure UTC
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    else:
        dt = dt.astimezone(timezone.utc)
    
    if is_all_day:
        return dt.strftime("%Y%m%d")
    return dt.strftime("%Y%m%dT%H%M%SZ")


class ICalService:
    @staticmethod
    def generate_calendar_feed(events: List[CalendarEvent], cal_name: str = "Calendario Familiar") -> str:
        now_str = format_dt(datetime.now(timezone.utc))
        lines = [
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//FamilyApp//Family Calendar 1.0//ES",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            f"X-WR-CALNAME:{escape_ical_text(cal_name)}",
            "X-WR-TIMEZONE:UTC",
        ]

        for ev in events:
            if ev.deleted_at is not None:
                continue
            
            lines.append("BEGIN:VEVENT")
            lines.append(f"UID:{ev.id}@familyapp")
            lines.append(f"DTSTAMP:{now_str}")
            
            created_str = format_dt(ev.created_at) if ev.created_at else now_str
            modified_str = format_dt(ev.updated_at) if ev.updated_at else now_str
            lines.append(f"CREATED:{created_str}")
            lines.append(f"LAST-MODIFIED:{modified_str}")

            lines.append(f"SUMMARY:{escape_ical_text(ev.title)}")
            if ev.description:
                lines.append(f"DESCRIPTION:{escape_ical_text(ev.description)}")

            if ev.is_all_day:
                lines.append(f"DTSTART;VALUE=DATE:{format_dt(ev.start_time, is_all_day=True)}")
                lines.append(f"DTEND;VALUE=DATE:{format_dt(ev.end_time, is_all_day=True)}")
            else:
                lines.append(f"DTSTART:{format_dt(ev.start_time)}")
                lines.append(f"DTEND:{format_dt(ev.end_time)}")

            lines.append(f"STATUS:CONFIRMED")
            lines.append("END:VEVENT")

        lines.append("END:VCALENDAR")
        # RFC 5545 requires CRLF line endings
        return "\r\n".join(lines) + "\r\n"
