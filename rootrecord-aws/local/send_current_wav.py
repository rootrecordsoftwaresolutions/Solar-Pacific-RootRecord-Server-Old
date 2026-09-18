#!/usr/bin/env python3
"""Send local current_*.wav / Current_*.wav report audio to the relay for AWS streamer.

Only files matching current_*.wav or Current_*.wav (no archives).
Uses RECV bot to post (so AWS SEND bot can see them once relay is a *channel*).
Until the relay is a Telegram channel, bot-to-bot delivery in a group will not work.
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

import httpx
import logging
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)

ETC = Path(__file__).resolve().parent / "etc"
API = "https://api.telegram.org"


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


def find_current_wavs() -> list[Path]:
    roots = [
        Path.home() / "Media",
        Path.home() / ".ollama" / "skills",
        Path.home() / ".ollama" / "skills" / "hybrid-reports" / "store",
    ]
    found: list[Path] = []
    patterns = ("current_*.wav", "Current_*.wav", "current.wav", "Current.wav")
    for root in roots:
        if not root.is_dir():
            continue
        for pat in patterns:
            found.extend(root.rglob(pat))
    # de-dupe, prefer newest mtime per basename
    by_name: dict[str, Path] = {}
    for p in found:
        if not p.is_file():
            continue
        key = p.name.lower()
        prev = by_name.get(key)
        if prev is None or p.stat().st_mtime > prev.stat().st_mtime:
            by_name[key] = p
    return sorted(by_name.values(), key=lambda p: p.name)


def main() -> None:
    load_dotenv()
    token = (
        os.environ.get("RR_DATAPACK_RECV_BOT_TOKEN")
        or os.environ.get("RR_DATAPACK_BOT_TOKEN")
        or ""
    ).strip()
    chat = (os.environ.get("RR_DATAPACK_CHAT_ID") or "").strip()
    if not token or not chat:
        print(json.dumps({"ok": False, "detail": "missing recv token or chat id"}))
        sys.exit(1)
    wavs = find_current_wavs()
    if not wavs:
        print(json.dumps({"ok": True, "sent": 0, "detail": "no current_*.wav found"}))
        return
    sent = []
    with httpx.Client(timeout=120.0) as client:
        for wav in wavs:
            # AWS streamer should save as Current-<basename> overwrite
            caption = f"RR_AUDIO Current {wav.name} @rootsender_bot"
            with wav.open("rb") as f:
                r = client.post(
                    f"{API}/bot{token}/sendDocument",
                    data={"chat_id": chat, "caption": caption},
                    files={"document": (wav.name, f)},
                )
            ok = r.status_code == 200 and bool(r.json().get("ok"))
            sent.append({"file": wav.name, "ok": ok, "bytes": wav.stat().st_size})
            if not ok:
                print(json.dumps({"ok": False, "detail": r.text[:300], "sent": sent}))
                sys.exit(1)
    print(json.dumps({"ok": True, "sent": len(sent), "files": sent}, indent=2))


if __name__ == "__main__":
    main()
