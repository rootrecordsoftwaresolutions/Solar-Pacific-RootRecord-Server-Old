#!/usr/bin/env python3
"""Publish prepared RootRecord report only on :00/:15/:30/:45 HST.

Never posts empty. Skips if no prep or already published for this mark.
Optional Telegram post when RR_PUBLISH_CHAT_ID is set.
"""
from __future__ import annotations

import json
import logging
import os
import shutil
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import httpx
import logging
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)

HST = ZoneInfo("Pacific/Honolulu")
log = logging.getLogger("rr.publish")
STORE = Path.home() / ".ollama" / "skills" / "rootrecord-aws" / "store"
PREP = STORE / "prep"
STATE = STORE / "state"
PUBLISHED = STORE / "published"
ETC = Path(__file__).resolve().parent / "etc"
API = "https://api.telegram.org"
MARKS = {0, 15, 30, 45}


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


def _read_json(path: Path) -> dict:
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def _alert_operator(msg: str) -> None:
    load_dotenv()
    token = (os.environ.get("RR_DATAPACK_BOT_TOKEN") or "").strip()
    chat = (os.environ.get("RR_ALERT_CHAT_ID") or os.environ.get("RR_CONTROL_CHAT_ID") or "").strip()
    log.warning("%s", msg)
    if not token or not chat:
        return
    try:
        httpx.post(
            f"{API}/bot{token}/sendMessage",
            json={"chat_id": chat, "text": f"RootRecord publish: {msg}"},
            timeout=20.0,
        )
    except Exception:
        pass


def run() -> dict:
    load_dotenv()
    PUBLISHED.mkdir(parents=True, exist_ok=True)
    STATE.mkdir(parents=True, exist_ok=True)
    now = datetime.now(HST)
    out: dict = {"ok": False, "hst": now.isoformat()}

    if now.minute not in MARKS:
        out["detail"] = f"not a publish mark (minute={now.minute})"
        out["skipped"] = True
        out["ok"] = True
        return out

    mark = now.strftime("%Y%m%d-%H%M")
    # Allow prep aimed at this mark (±1 min clock skew): prefer exact file
    prep_file = PREP / f"prep-{mark}.md"
    current = PREP / "prep-current.md"
    prep_meta = _read_json(STATE / "prep.json")
    pub_state = _read_json(STATE / "publish.json")

    if pub_state.get("last_published_mark") == mark:
        out.update({"ok": True, "skipped": True, "detail": "already published this mark", "mark": mark})
        return out

    src = prep_file if prep_file.is_file() else current
    if not src.is_file():
        _alert_operator(f"skip empty — no prep for {mark}")
        out.update({"ok": True, "skipped": True, "detail": "no_prep", "mark": mark})
        (STATE / "publish.json").write_text(
            json.dumps({**pub_state, "last_skip": out}, indent=2) + "\n", encoding="utf-8"
        )
        return out

    body = src.read_text(encoding="utf-8").strip()
    if not body or "Content hash" not in body:
        _alert_operator(f"skip empty — prep unusable for {mark}")
        out.update({"ok": True, "skipped": True, "detail": "empty_prep", "mark": mark})
        return out

    # If content hash unchanged vs last publish and operator wants quiet, still archive
    dest = PUBLISHED / f"published-{mark}.md"
    dest.write_text(body + "\n", encoding="utf-8")
    shutil.copy2(src, PUBLISHED / "published-current.md")

    # Optional public post
    token = (os.environ.get("RR_DATAPACK_BOT_TOKEN") or "").strip()
    pub_chat = (os.environ.get("RR_PUBLISH_CHAT_ID") or "").strip()
    posted = False
    if token and pub_chat:
        # Telegram caption limit — send as document
        try:
            with dest.open("rb") as f:
                r = httpx.post(
                    f"{API}/bot{token}/sendDocument",
                    data={"chat_id": pub_chat, "caption": f"RootRecord {mark} HST"},
                    files={"document": (dest.name, f)},
                    timeout=60.0,
                )
            posted = r.status_code == 200 and bool(r.json().get("ok"))
        except Exception as exc:
            log.warning("publish telegram failed: %s", exc)

    out.update(
        {
            "ok": True,
            "mark": mark,
            "path": str(dest),
            "posted": posted,
            "content_hash": prep_meta.get("content_hash"),
        }
    )
    (STATE / "publish.json").write_text(
        json.dumps({"last_published_mark": mark, "last_publish": out}, indent=2) + "\n",
        encoding="utf-8",
    )
    ready = STATE / "publish-ready"
    if ready.exists():
        ready.unlink()
    return out


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    print(json.dumps(run(), indent=2))


if __name__ == "__main__":
    main()
