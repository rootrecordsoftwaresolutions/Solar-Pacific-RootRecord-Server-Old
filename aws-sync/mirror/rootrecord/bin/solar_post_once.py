#!/usr/bin/env python3
"""Post AWS solar-cam GIF (or still) to the council group. Media stays on rr-aws."""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import WORK, load_dotenv, now_hst, write_json

API = "https://api.telegram.org"
CAPTION = "Solar panels cam — latest loop from the hour. Ask again anytime."
COOLDOWN_S = 60.0
STATE = WORK / "solar-cam" / "last-post.json"
DEST = WORK / "solar-cam"


def _token() -> str:
    return (
        (os.environ.get("RR_SOLAR_BOT_TOKEN") or "").strip()
        or (os.environ.get("RR_RADAR_BOT_TOKEN") or "").strip()
        or (os.environ.get("TELEGRAM_BRUCE_TOKEN") or "").strip()
    )


def _send_doc(client: httpx.Client, token: str, chat: str, path: Path, caption: str, reply_to: int, mime: str) -> int | None:
    data: dict = {"chat_id": chat, "caption": caption}
    if reply_to:
        data["reply_to_message_id"] = int(reply_to)
    with path.open("rb") as fh:
        r = client.post(
            f"{API}/bot{token}/sendDocument",
            data=data,
            files={"document": (path.name, fh, mime)},
            timeout=60.0,
        )
    body = r.json() if r.content else {}
    if not body.get("ok"):
        print(body, file=sys.stderr)
        return None
    mid = ((body.get("result") or {}) if isinstance(body.get("result"), dict) else {}).get("message_id")
    return int(mid) if mid is not None else 0


def main() -> int:
    load_dotenv()
    ap = argparse.ArgumentParser()
    ap.add_argument("--chat-id", default=os.environ.get("RR_SOLAR_CHAT_ID") or os.environ.get("RR_RADAR_CHAT_ID") or "")
    ap.add_argument("--reply-to", type=int, default=0)
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--still", action="store_true", help="Prefer Current.jpg over GIF")
    args = ap.parse_args()
    chat = str(args.chat_id or "").strip()
    token = _token()
    if not chat or not token:
        print("missing chat/token", file=sys.stderr)
        return 2

    if not args.force and STATE.is_file():
        try:
            prev = json.loads(STATE.read_text(encoding="utf-8"))
            if time.time() - float(prev.get("t") or 0) < COOLDOWN_S and str(prev.get("chat_id")) == chat:
                print("cooldown")
                return 0
        except Exception:
            pass

    gif = DEST / "Current.gif"
    jpg = DEST / "Current.jpg"
    path: Path | None = None
    mime = "image/gif"
    if args.still and jpg.is_file() and jpg.stat().st_size > 0:
        path, mime = jpg, "image/jpeg"
    elif gif.is_file() and gif.stat().st_size > 0:
        path, mime = gif, "image/gif"
    elif jpg.is_file() and jpg.stat().st_size > 0:
        path, mime = jpg, "image/jpeg"
    if path is None:
        print("no Current.gif/jpg — is rr-solar-cam running?", file=sys.stderr)
        return 3

    with httpx.Client() as client:
        mid = _send_doc(client, token, chat, path, CAPTION, args.reply_to, mime)
        if mid is None:
            return 1

    write_json(
        STATE,
        {"ok": True, "t": time.time(), "chat_id": chat, "hst": now_hst().isoformat(), "file": path.name},
    )
    print("ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
