"""Owner-only last addressing snapshot (no secrets)."""
from __future__ import annotations

from typing import Any


def remember(st: dict[str, Any], payload: dict[str, Any]) -> None:
    st["last_address"] = payload


def format_last(st: dict[str, Any]) -> str:
    last = st.get("last_address") if isinstance(st.get("last_address"), dict) else {}
    if not last:
        return "No address decision yet."
    voices = last.get("voices") or []
    jobs = last.get("job_ids") or []
    text = str(last.get("text") or "")[:240].replace("\n", " ")
    return (
        f"reason={last.get('reason') or '?'}\n"
        f"voices={', '.join(voices) if voices else '(none)'}\n"
        f"message_id={last.get('message_id') or '-'}\n"
        f"reply_to={last.get('reply_to') or '-'}\n"
        f"job_ids={', '.join(str(j) for j in jobs) if jobs else '(none)'}\n"
        f"text={text}"
    )
