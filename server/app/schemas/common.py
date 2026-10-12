import re
from datetime import datetime
from typing import Any, Optional


def empty_str_to_none(v: Any) -> Any:
    if isinstance(v, str):
        v = v.strip()
        if not v:
            return None
    return v


def parse_tolerant_datetime(v: Any) -> Any:
    if v is None or isinstance(v, datetime):
        return v
    if isinstance(v, str):
        v = v.strip()
        if not v:
            return None
        # Replace space between date and time with T
        if " " in v and "T" not in v:
            v = v.replace(" ", "T")
        # Fix single digit hour after T: e.g. T9: -> T09:
        v = re.sub(r"T(\d):", r"T0\1:", v)
        # If no seconds e.g. T09:00Z -> T09:00:00Z
        v = re.sub(r"T(\d{2}):(\d{2})Z$", r"T\1:\2:00Z", v)
        v = re.sub(r"T(\d{2}):(\d{2})$", r"T\1:\2:00", v)
        try:
            return datetime.fromisoformat(v)
        except Exception:
            return v
    return v
