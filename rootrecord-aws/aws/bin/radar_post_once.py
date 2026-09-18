#!/usr/bin/env python3
"""Post AWS radar GIF (+ optional weather brief) to the council group.

Puller: rr-radar → work/radar/Current.gif
Weather facts: work/weather + work/noaa Current.*
Media send is AWS-only.
"""
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
from weather_brief import format_brief

API = "https://api.telegram.org"
CAPTION = (
    "Here's the latest radar and weather information for you. "
    "Let me know if theres anything else I can relay."
)
COOLDOWN_S = 90.0
STATE = WORK / "radar" / "last-post.json"


def _token() -> str:
    return (
        (os.environ.get("RR_RADAR_BOT_TOKEN") or "").strip()
        or (os.environ.get("TELEGRAM_BRUCE_TOKEN") or "").strip()
    )


def _send_document(client: httpx.Client, token: str, chat: str, path: Path, caption: str, reply_to: int) -> int | None:
    data: dict = {"chat_id": chat, "caption": caption}
    if reply_to:
        data["reply_to_message_id"] = int(reply_to)
    with path.open("rb") as fh:
        r = client.post(
            f"{API}/bot{token}/sendDocument",
            data=data,
            files={"document": (path.name, fh, "image/gif")},
            timeout=60.0,
        )
    body = r.json() if r.content else {}
    if not body.get("ok"):
        print(body, file=sys.stderr)
        return None
    mid = ((body.get("result") or {}) if isinstance(body.get("result"), dict) else {}).get("message_id")
    return int(mid) if mid is not None else 0


def _send_text(client: httpx.Client, token: str, chat: str, text: str, reply_to: int | None) -> bool:
    payload: dict = {"chat_id": chat, "text": text[:4000], "disable_web_page_preview": True}
    if reply_to:
        payload["reply_to_message_id"] = int(reply_to)
    r = client.post(f"{API}/bot{token}/sendMessage", json=payload, timeout=30.0)
    body = r.json() if r.content else {}
    return bool(body.get("ok"))


def main() -> int:
    load_dotenv()
    ap = argparse.ArgumentParser()
    ap.add_argument("--chat-id", default=os.environ.get("RR_RADAR_CHAT_ID") or "")
    ap.add_argument("--reply-to", type=int, default=0)
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--gif-only", action="store_true", help="Radar GIF only (for weather report casts)")
    ap.add_argument("--weather-only", action="store_true", help="Weather brief only, no GIF")
    args = ap.parse_args()
    chat = str(args.chat_id or "").strip()
    token = _token()
    gif = WORK / "radar" / "Current.gif"
    if not chat or not token:
        print("missing chat/token", file=sys.stderr)
        return 2

    if not args.force and STATE.is_file() and not args.weather_only:
        try:
            prev = json.loads(STATE.read_text(encoding="utf-8"))
            if time.time() - float(prev.get("t") or 0) < COOLDOWN_S and str(prev.get("chat_id")) == chat:
                print("cooldown")
                return 0
        except Exception:
            pass

    with httpx.Client() as client:
        gif_mid = None
        if not args.weather_only:
            if not gif.is_file() or gif.stat().st_size <= 0:
                print("no Current.gif — is rr-radar running?", file=sys.stderr)
                return 3
            gif_mid = _send_document(client, token, chat, gif, CAPTION, args.reply_to)
            if gif_mid is None:
                return 1

        if not args.gif_only:
            brief = format_brief()
            reply = gif_mid if gif_mid not in (None, 0) else (args.reply_to or None)
            if not _send_text(client, token, chat, brief, reply if reply else None):
                print("weather brief send failed", file=sys.stderr)
                # GIF already posted — soft fail
                if args.weather_only:
                    return 1

    write_json(
        STATE,
        {
            "ok": True,
            "t": time.time(),
            "chat_id": chat,
            "hst": now_hst().isoformat(),
            "gif": not args.weather_only,
            "weather": not args.gif_only,
        },
    )
    print("ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
