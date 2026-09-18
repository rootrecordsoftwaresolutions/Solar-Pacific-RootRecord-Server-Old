#!/usr/bin/env python3
"""RootRecord channel poller (~1s) — chatlogs + reply-now triggers.

Requires RR_TELEGRAM_BOT_TOKEN and optional RR_WATCH_CHAT_IDS (comma-separated).
Posts tiny trigger notices to RR_CONTROL_CHAT_ID when set (reply-now path).
Does not run LLM speak — that stays on AVA-CORE.
"""
from __future__ import annotations

import json
import logging
import os
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import WORK, append_jsonl, ensure_dirs, load_dotenv, now_hst, write_json

log = logging.getLogger("rr.chat")
API = "https://api.telegram.org"
INTERVAL_S = 1.0
OFFSET_PATH = WORK / "chatlogs" / "tg-offset.json"


def _token() -> str:
    return (os.environ.get("RR_TELEGRAM_BOT_TOKEN") or "").strip()


def _watch_ids() -> set[str]:
    raw = (os.environ.get("RR_WATCH_CHAT_IDS") or "").strip()
    if not raw:
        return set()
    return {x.strip() for x in raw.split(",") if x.strip()}


def _control_chat() -> str:
    return (os.environ.get("RR_CONTROL_CHAT_ID") or "").strip()


def _load_offset() -> int:
    if not OFFSET_PATH.is_file():
        return 0
    try:
        data = json.loads(OFFSET_PATH.read_text(encoding="utf-8"))
        return int(data.get("offset") or 0)
    except (OSError, json.JSONDecodeError, TypeError, ValueError):
        return 0


def _save_offset(offset: int) -> None:
    write_json(OFFSET_PATH, {"offset": offset, "updated_at": now_hst().isoformat()})


def _is_trigger(text: str) -> bool:
    t = text.strip().lower()
    if not t:
        return False
    # Address RootRecord agents / common wake words
    wakes = ("ava ", "ava,", "@ava", "bruce ", "bruce,", "carly ", "carly,", "/ask", "hey ava")
    return t.startswith(wakes) or any(w.strip() in t for w in ("@ava", "@bruce", "@carly"))


def _post_control(client: httpx.Client, token: str, payload: dict) -> None:
    chat = _control_chat()
    if not chat:
        return
    body = {
        "chat_id": chat,
        "text": "RR_TRIGGER " + json.dumps(payload, separators=(",", ":")),
        "disable_notification": True,
    }
    try:
        client.post(f"{API}/bot{token}/sendMessage", json=body, timeout=15.0)
    except Exception as exc:
        log.debug("control notify failed: %s", exc)


def poll_once(client: httpx.Client, token: str) -> int:
    ensure_dirs()
    offset = _load_offset()
    params = {"timeout": 0, "limit": 100}
    if offset:
        params["offset"] = offset
    r = client.get(f"{API}/bot{token}/getUpdates", params=params, timeout=20.0)
    r.raise_for_status()
    data = r.json()
    if not data.get("ok"):
        raise RuntimeError(str(data)[:200])
    updates = data.get("result") or []
    watch = _watch_ids()
    max_update = offset
    day = now_hst().strftime("%Y%m%d")
    # Overwrite-friendly current log (wiped each pack); keep one rolling Current.jsonl
    log_path = WORK / "chatlogs" / "Current.jsonl"
    for upd in updates:
        uid = int(upd.get("update_id") or 0)
        if uid >= max_update:
            max_update = uid + 1
        msg = upd.get("message") or upd.get("edited_message") or upd.get("channel_post") or {}
        chat = msg.get("chat") or {}
        chat_id = str(chat.get("id") or "")
        if watch and chat_id not in watch:
            continue
        text = str(msg.get("text") or msg.get("caption") or "")
        row = {
            "t": time.time(),
            "hst": now_hst().isoformat(),
            "update_id": uid,
            "chat_id": chat_id,
            "chat_type": chat.get("type"),
            "from_id": (msg.get("from") or {}).get("id"),
            "text": text[:4000],
            "message_id": msg.get("message_id"),
        }
        append_jsonl(log_path, row)
        if text and _is_trigger(text):
            trigger = {
                "hst": now_hst().isoformat(),
                "chat_id": chat_id,
                "message_id": msg.get("message_id"),
                "text": text[:2000],
                "from_id": row["from_id"],
            }
            write_json(WORK / "triggers" / "Current.json", trigger)
            _post_control(client, token, trigger)
            log.info("trigger chat=%s", chat_id)
    if max_update != offset:
        _save_offset(max_update)
    write_json(
        WORK / "chatlogs" / "Current.meta.json",
        {
            "ok": True,
            "offset": max_update,
            "updates": len(updates),
            "updated_at": now_hst().isoformat(),
        },
    )
    return len(updates)


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    load_dotenv()
    token = _token()
    if not token:
        log.error("RR_TELEGRAM_BOT_TOKEN missing in etc/secrets.env — chat poller idle")
        while True:
            time.sleep(60)
    log.info("RootRecord chat poller start interval=%ss", INTERVAL_S)
    with httpx.Client() as client:
        while True:
            started = time.monotonic()
            try:
                n = poll_once(client, token)
                if n:
                    log.info("chat updates=%s", n)
            except Exception as exc:
                log.warning("chat poll failed: %s", exc)
            elapsed = time.monotonic() - started
            time.sleep(max(0.05, INTERVAL_S - elapsed))


if __name__ == "__main__":
    main()
