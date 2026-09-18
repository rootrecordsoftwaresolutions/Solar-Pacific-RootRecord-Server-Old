#!/usr/bin/env python3
"""AWS: receive RR_AUDIO docs from relay → work/audio/Current-*.wav (overwrite).

Run as service. Uses SEND bot getUpdates (sees RECV posts when relay is a channel).
Groups cannot deliver bot-to-bot — convert relay to a channel.
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
from common import WORK, ensure_dirs, load_dotenv, now_hst, write_json

log = logging.getLogger("rr.audio_recv")
API = "https://api.telegram.org"
OFFSET = WORK / "audio" / "tg-offset.json"


def _token() -> str:
    return (
        os.environ.get("RR_DATAPACK_SEND_BOT_TOKEN")
        or os.environ.get("RR_DATAPACK_BOT_TOKEN")
        or ""
    ).strip()


def _chat() -> str:
    return (os.environ.get("RR_DATAPACK_CHAT_ID") or "").strip()


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    load_dotenv()
    token, chat = _token(), _chat()
    if not token:
        log.error("no send token — audio recv idle")
        while True:
            time.sleep(60)
    ensure_dirs()
    (WORK / "audio").mkdir(parents=True, exist_ok=True)
    offset = 0
    if OFFSET.is_file():
        try:
            offset = int(json.loads(OFFSET.read_text()).get("offset") or 0)
        except Exception:
            offset = 0
    chat_ids = {chat}
    if chat.startswith("-") and not chat.startswith("-100"):
        chat_ids.add("-100" + chat.lstrip("-"))
    with httpx.Client(timeout=60.0) as client:
        while True:
            params = {
                "timeout": 20,
                "limit": 50,
                "allowed_updates": json.dumps(["channel_post", "message"]),
            }
            if offset:
                params["offset"] = offset
            try:
                r = client.get(f"{API}/bot{token}/getUpdates", params=params)
                r.raise_for_status()
                data = r.json()
            except Exception as exc:
                log.warning("getUpdates failed: %s", exc)
                time.sleep(3)
                continue
            if not data.get("ok"):
                time.sleep(2)
                continue
            for upd in data.get("result") or []:
                uid = int(upd.get("update_id") or 0)
                offset = max(offset, uid + 1)
                msg = upd.get("channel_post") or upd.get("message") or {}
                if str((msg.get("chat") or {}).get("id") or "") not in chat_ids:
                    continue
                caption = str(msg.get("caption") or msg.get("text") or "")
                doc = msg.get("document") or {}
                name = str(doc.get("file_name") or "")
                if not name.lower().endswith(".wav"):
                    continue
                if "RR_AUDIO" not in caption and not name.lower().startswith("current"):
                    continue
                file_id = doc.get("file_id")
                if not file_id:
                    continue
                fr = client.get(f"{API}/bot{token}/getFile", params={"file_id": file_id})
                fr.raise_for_status()
                fdata = fr.json()
                if not fdata.get("ok"):
                    continue
                fp = fdata["result"]["file_path"]
                # Always Current-<original> overwrite
                safe = name if name.lower().startswith("current") else f"Current-{name}"
                dest = WORK / "audio" / safe
                with client.stream("GET", f"{API}/file/bot{token}/{fp}") as resp:
                    resp.raise_for_status()
                    dest.write_bytes(resp.read())
                write_json(
                    WORK / "audio" / "Current.meta.json",
                    {
                        "ok": True,
                        "file": safe,
                        "bytes": dest.stat().st_size,
                        "updated_at": now_hst().isoformat(),
                    },
                )
                log.info("audio Current saved %s", safe)
            write_json(OFFSET, {"offset": offset, "updated_at": now_hst().isoformat()})


if __name__ == "__main__":
    main()
