#!/usr/bin/env python3
"""Watch control-channel RR_TRIGGER messages for reply-now while console is up.

Writes triggers into store/triggers for council/LLM consumers on AVA-CORE.
Does not call the NPU itself.
"""
from __future__ import annotations

import json
import logging
import os
import time
from pathlib import Path

import httpx
import logging
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)

log = logging.getLogger("rr.trigger_watch")
STORE = Path.home() / ".ollama" / "skills" / "rootrecord-aws" / "store"
TRIG = STORE / "triggers"
STATE = STORE / "state"
ETC = Path(__file__).resolve().parent / "etc"
API = "https://api.telegram.org"
CONSOLE_UP = Path("/run/user") # resolved at runtime


def load_dotenv() -> None:
    path = ETC / "secrets.env"
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        k, v = k.strip(), v.strip().strip("'").strip('"')
        if k and k not in os.environ:
            os.environ[k] = v


def console_up() -> bool:
    # Match AVA Console flag used by launch/idle-stop
    candidates = [
        Path.home() / ".ollama" / "skills" / "state" / "store" / "ava-console-up",
        Path.home() / ".local" / "state" / "ava-console-up",
        Path("/tmp/ava-console-up"),
    ]
    xdg = os.environ.get("XDG_RUNTIME_DIR")
    if xdg:
        candidates.insert(0, Path(xdg) / "ava-console-up")
    return any(p.exists() for p in candidates)


def _offset() -> int:
    p = STATE / "trigger-watch-offset.json"
    if not p.is_file():
        return 0
    try:
        return int(json.loads(p.read_text(encoding="utf-8")).get("offset") or 0)
    except (OSError, json.JSONDecodeError, TypeError, ValueError):
        return 0


def _save_offset(n: int) -> None:
    STATE.mkdir(parents=True, exist_ok=True)
    (STATE / "trigger-watch-offset.json").write_text(
        json.dumps({"offset": n}) + "\n", encoding="utf-8"
    )


def tick(client: httpx.Client, token: str, control_chat: str) -> int:
    if not console_up():
        return 0
    offset = _offset()
    params = {"timeout": 0, "limit": 50}
    if offset:
        params["offset"] = offset
    r = client.get(f"{API}/bot{token}/getUpdates", params=params, timeout=20.0)
    r.raise_for_status()
    data = r.json()
    if not data.get("ok"):
        return 0
    max_u = offset
    n = 0
    TRIG.mkdir(parents=True, exist_ok=True)
    for upd in data.get("result") or []:
        uid = int(upd.get("update_id") or 0)
        if uid >= max_u:
            max_u = uid + 1
        msg = upd.get("message") or upd.get("channel_post") or {}
        chat_id = str((msg.get("chat") or {}).get("id") or "")
        if control_chat and chat_id != str(control_chat):
            continue
        text = str(msg.get("text") or "")
        if not text.startswith("RR_TRIGGER "):
            continue
        raw = text[len("RR_TRIGGER ") :]
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = {"text": raw}
        path = TRIG / f"live-{uid}.json"
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        (TRIG / "latest.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        n += 1
        log.info("reply-now trigger saved %s", path.name)
    if max_u != offset:
        _save_offset(max_u)
    return n


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    load_dotenv()
    token = (os.environ.get("RR_DATAPACK_BOT_TOKEN") or os.environ.get("RR_TELEGRAM_BOT_TOKEN") or "").strip()
    control = (os.environ.get("RR_CONTROL_CHAT_ID") or "").strip()
    if not token:
        log.error("no bot token — trigger watch idle")
        while True:
            time.sleep(60)
    log.info("RootRecord trigger watch (console-gated)")
    with httpx.Client() as client:
        while True:
            try:
                tick(client, token, control)
            except Exception as exc:
                log.warning("tick failed: %s", exc)
            time.sleep(1.0)


if __name__ == "__main__":
    main()
