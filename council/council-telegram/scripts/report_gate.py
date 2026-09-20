"""Council publishes queued public reports via local origin. Operator does not approve."""
from __future__ import annotations

import json
import urllib.error
import urllib.request
from typing import Any
from urllib.parse import quote

ORIGIN = "http://127.0.0.1:8787"
MAX_PER_TICK = 5


def _req(method: str, path: str, *, data: bytes | None = None) -> dict[str, Any]:
    url = ORIGIN.rstrip("/") + path
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")[:400]
        return {"ok": False, "detail": f"http_{e.code}", "body": body}
    except OSError as e:
        return {"ok": False, "detail": type(e).__name__}
    try:
        parsed = json.loads(raw) if raw else {}
    except json.JSONDecodeError:
        return {"ok": False, "detail": "bad_json"}
    return parsed if isinstance(parsed, dict) else {"ok": False, "detail": "not_object"}


def list_drafts() -> list[dict[str, Any]]:
    data = _req("GET", "/api/reports/queue")
    items = data.get("items") if isinstance(data.get("items"), list) else []
    return [x for x in items if isinstance(x, dict) and x.get("name")]


def publish_named(name: str) -> dict[str, Any]:
    safe = quote(str(name or ""), safe="")
    return _req("POST", f"/api/reports/queue/{safe}/publish", data=b"{}")


def archive_unposted(*, keep: int = 0) -> dict[str, Any]:
    """Move leftover drafts off the operator queue without posting them."""
    from apps.core.services import reports

    items = (reports.list_queue() or {}).get("items") or []
    keep = max(0, int(keep))
    moved = 0
    dest = reports.queue_dir() / "skipped"
    dest.mkdir(parents=True, exist_ok=True)
    for row in items[keep:]:
        name = str(row.get("name") or "")
        src = reports.queue_dir() / name
        if not src.is_file():
            continue
        src.rename(dest / name)
        moved += 1
    return {"ok": True, "skipped": moved, "kept": keep}


def publish_pending(*, limit: int = MAX_PER_TICK) -> dict[str, Any]:
    items = list_drafts()
    if not items:
        return {"ok": True, "published": 0, "names": []}
    names: list[str] = []
    failed: list[str] = []
    for row in items[: max(1, int(limit))]:
        name = str(row.get("name") or "")
        out = publish_named(name)
        if out.get("ok"):
            names.append(name)
        else:
            failed.append(name)
    left = max(0, len(items) - len(names))
    archived = {"skipped": 0}
    if left:
        archived = archive_unposted(keep=0)
    return {
        "ok": not failed,
        "published": len(names),
        "names": names,
        "failed": failed,
        "queued_left": 0,
        "skipped": archived.get("skipped") or 0,
    }
