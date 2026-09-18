#!/usr/bin/env python3
"""Send live report Current audio to the Telegram relay for AWS radio.

Only *-current.wav / *_current.wav style report files (see speakers.is_current_audio).
Never word-bank clips. Dedupes by sha so we only send when content changes.
"""
from __future__ import annotations

import hashlib
import json
import logging
import os
import sys
from pathlib import Path

import httpx

logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)

ETC = Path(__file__).resolve().parent / "etc"
STATE = Path(__file__).resolve().parents[1] / "store" / "state" / "audio-send.json"
API = "https://api.telegram.org"

# Report audio roots only (never Media/public/audio/words)
SEARCH_ROOTS = [
    Path.home() / "Media" / "public" / "audio" / "current",
    Path.home() / "Media" / "public" / "audio" / "voice" / "generated",
    Path.home() / "Media" / "public" / "audio" / "reports",
    Path.home() / "Media" / "reports",
    Path.home() / ".ollama" / "skills" / "energy-report" / "store" / "audio",
]


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


def _is_current_audio(path: Path) -> bool:
    stem = path.stem.lower()
    if "/words/" in str(path).replace("\\", "/").lower():
        return False
    if "word" in path.parts:
        return False
    return (
        stem.endswith("-current")
        or stem.endswith("_current")
        or stem in ("nws_official_statement", "kilauea_current", "current")
        or stem.startswith("current_")
        or stem.startswith("current-")
    )


def find_current_wavs() -> list[Path]:
    found: list[Path] = []
    for root in SEARCH_ROOTS:
        if not root.is_dir():
            continue
        for p in root.rglob("*.wav"):
            if not p.is_file() or p.stat().st_size < 1000:
                continue
            if _is_current_audio(p):
                found.append(p)
    by_name: dict[str, Path] = {}
    for p in found:
        key = p.name.lower()
        prev = by_name.get(key)
        if prev is None or p.stat().st_mtime > prev.stat().st_mtime:
            by_name[key] = p
    return sorted(by_name.values(), key=lambda p: p.stat().st_mtime, reverse=True)


def _load_sent() -> dict:
    if not STATE.is_file():
        return {}
    try:
        return json.loads(STATE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def _save_sent(data: dict) -> None:
    STATE.parent.mkdir(parents=True, exist_ok=True)
    tmp = STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(STATE)


def _sha12(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:12]


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
    sent_state = _load_sent()
    hashes = sent_state.get("hashes") or {}
    to_send: list[Path] = []
    for wav in wavs:
        h = _sha12(wav)
        if hashes.get(wav.name) == h:
            continue
        to_send.append(wav)
    if not to_send:
        print(json.dumps({"ok": True, "sent": 0, "detail": "no new current report wavs", "known": len(wavs)}))
        return
    sent = []
    with httpx.Client(timeout=180.0) as client:
        for wav in to_send:
            caption = f"RR_AUDIO Current {wav.name}"
            with wav.open("rb") as f:
                r = client.post(
                    f"{API}/bot{token}/sendDocument",
                    data={"chat_id": chat, "caption": caption},
                    files={"document": (wav.name, f)},
                )
            ok = r.status_code == 200 and bool(r.json().get("ok"))
            entry = {"file": wav.name, "ok": ok, "bytes": wav.stat().st_size, "sha12": _sha12(wav)}
            sent.append(entry)
            if ok:
                hashes[wav.name] = entry["sha12"]
            else:
                print(json.dumps({"ok": False, "detail": r.text[:300], "sent": sent}))
                _save_sent({**sent_state, "hashes": hashes})
                sys.exit(1)
    _save_sent({"hashes": hashes, "last_sent": sent})
    print(json.dumps({"ok": True, "sent": len(sent), "files": sent}, indent=2))


if __name__ == "__main__":
    main()
