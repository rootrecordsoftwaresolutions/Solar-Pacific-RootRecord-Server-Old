"""Cursor Cloud Agents API — only after /approve.

POST https://api.cursor.com/v1/agents with Bearer CURSOR_API_KEY.
Empty key = friendly degrade (no local coder models, no cursor agent CLI).
"""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from typing import Any

from .config import CURSOR_AGENTS_URL, Config


def _headers(cfg: Config) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {cfg.cursor_api_key}",
        "Content-Type": "application/json",
    }


def launch_agent(cfg: Config, prompt_text: str) -> dict[str, Any]:
    if not (cfg.cursor_api_key or "").strip():
        return {
            "ok": False,
            "degraded": True,
            "message": "Implementer isn’t plugged in yet. Approval recorded; no agent started.",
        }
    body = {
        "prompt": {"text": prompt_text},
        "repos": [
            {
                "url": cfg.cursor_default_repo,
                "startingRef": "main",
            }
        ],
        "autoCreatePR": True,
    }
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        CURSOR_AGENTS_URL,
        data=data,
        headers=_headers(cfg),
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            payload = json.loads(resp.read().decode("utf-8", errors="replace"))
        return {"ok": True, "degraded": False, "data": payload}
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8", errors="replace") if e.fp else str(e)
        return {"ok": False, "degraded": False, "message": f"Implementer HTTP {e.code}", "detail": err[:500]}
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "degraded": False, "message": str(e)}


def poll_agent(cfg: Config, agent_id: str, max_wait_s: int = 600) -> dict[str, Any]:
    if not cfg.cursor_api_key:
        return {"ok": False, "message": "no key"}
    url = f"{CURSOR_AGENTS_URL.rstrip('/')}/{agent_id}"
    deadline = time.time() + max_wait_s
    last: dict[str, Any] = {}
    while time.time() < deadline:
        req = urllib.request.Request(url, headers=_headers(cfg), method="GET")
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                last = json.loads(resp.read().decode("utf-8", errors="replace"))
        except Exception as e:  # noqa: BLE001
            return {"ok": False, "message": str(e), "last": last}
        status = str(
            last.get("status")
            or (last.get("agent") or {}).get("status")
            or ""
        ).upper()
        if status in ("FINISHED", "ERROR", "FAILED", "COMPLETED", "CANCELLED"):
            return {"ok": status in ("FINISHED", "COMPLETED"), "status": status, "data": last}
        time.sleep(5)
    return {"ok": False, "message": "timeout", "last": last}


def extract_pr_url(payload: dict[str, Any]) -> str:
    if not payload:
        return ""

    def _walk(obj: Any, depth: int = 0) -> str:
        if depth > 6:
            return ""
        if isinstance(obj, str):
            if "github.com" in obj and "/pull/" in obj:
                return obj
            return ""
        if isinstance(obj, dict):
            for key in ("prUrl", "pr_url", "pullRequestUrl", "url", "target"):
                if obj.get(key):
                    found = _walk(obj.get(key), depth + 1)
                    if found:
                        return found
            for val in obj.values():
                found = _walk(val, depth + 1)
                if found:
                    return found
        if isinstance(obj, list):
            for item in obj:
                found = _walk(item, depth + 1)
                if found:
                    return found
        return ""

    found = _walk(payload)
    if found:
        return found
    text = json.dumps(payload)
    import re

    m = re.search(r"https://github\.com/[^\"\s]+/pull/\d+", text)
    return m.group(0) if m else ""
