"""Council vision — moondream on Ollama, then sort into Media. Unloads after look."""
from __future__ import annotations

import hashlib
import json
import re
import shutil
import time
from pathlib import Path
from typing import Any

from .config import CONFIG_DIR, Config
from . import telegram

MEDIA_ROOT = Path.home() / "Media" / "public" / "images" / "chat-vision"
INBOX = MEDIA_ROOT / "inbox"
SORTED = MEDIA_ROOT / "sorted"
LOG_PATH = CONFIG_DIR / "vision-log.jsonl"

# Folder tags from vision text (first match wins order below).
SORT_RULES: list[tuple[str, tuple[str, ...]]] = [
    (
        "weather",
        (
            "rain",
            "storm",
            "cloud",
            "flood",
            "lightning",
            "thunder",
            "radar",
            "forecast",
            "umbrella",
        ),
    ),
    (
        "kilauea",
        ("volcano", "lava", "erupt", "kilauea", "crater", "vent", "plume", "ash"),
    ),
    (
        "ecoflow",
        ("battery", "generator", "solar", "ecoflow", "inverter", "power station", "panel"),
    ),
    (
        "minecraft",
        ("minecraft", "pixel", "blocky", "creeper", "villager"),
    ),
    (
        "games",
        (
            "game",
            "gaming",
            "skylines",
            "city builder",
            "simcity",
            "controller",
            "xbox",
            "playstation",
            "steam",
        ),
    ),
    (
        "maps",
        ("map", "chart", "graph", "diagram", "screenshot of a map"),
    ),
    (
        "people",
        ("person", "people", "man", "woman", "girl", "boy", "face", "portrait", "selfie"),
    ),
    (
        "screens",
        ("screenshot", "monitor", "desktop", "ui ", "interface", "terminal"),
    ),
]

PROMPT = (
    "Describe this image in four short factual sentences. "
    "Name what it shows. Note any readable text. "
    "Say the setting. Do not invent brand names you cannot read."
)


def _now() -> int:
    return int(time.time())


def _log(row: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a", encoding="utf-8") as f:
        f.write(json.dumps(row, sort_keys=True) + "\n")


def pick_folder(description: str) -> str:
    low = (description or "").lower()
    for name, kws in SORT_RULES:
        if any(k in low for k in kws):
            return name
    return "unsorted"


def look(path: Path, *, prompt: str = PROMPT) -> str | None:
    """One-shot moondream via Ollama. keep_alive 0 so NPU chat stays free."""
    from apps.core.services import ollama as oc

    return oc.look_sync(prompt, [path], timeout=120)


def download_chat_photo(cfg: Config, message: dict[str, Any]) -> Path | None:
    fid = telegram.largest_photo_file_id(message)
    if not fid:
        return None
    token = cfg.token_for("ava")
    meta = telegram.get_file(token, fid)
    if not meta.get("ok"):
        print(f"vision getFile fail {meta.get('description')}", flush=True)
        return None
    result = meta.get("result") if isinstance(meta.get("result"), dict) else {}
    remote = str(result.get("file_path") or "")
    if not remote:
        return None
    digest = hashlib.sha1(fid.encode("utf-8")).hexdigest()[:12]
    ext = Path(remote).suffix.lower() or ".jpg"
    if ext not in {".jpg", ".jpeg", ".png", ".webp", ".gif"}:
        ext = ".jpg"
    INBOX.mkdir(parents=True, exist_ok=True)
    dest = INBOX / f"{_now()}-{digest}{ext}"
    return telegram.download_file(token, remote, dest)


def sort_copy(src: Path, folder: str) -> Path:
    dest_dir = SORTED / folder
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / src.name
    if not dest.exists():
        shutil.copy2(src, dest)
    return dest


def analyze_and_sort(path: Path, *, caption: str = "") -> dict[str, Any]:
    desc = look(path) or ""
    desc = re.sub(r"\s+", " ", desc).strip()
    name_hint = path.name.replace("-", " ").replace("_", " ")
    folder = pick_folder(f"{desc} {caption or ''} {name_hint}")
    dest = sort_copy(path, folder) if path.is_file() else path
    row = {
        "ts": _now(),
        "src": str(path),
        "sorted": str(dest),
        "folder": folder,
        "caption": (caption or "")[:200],
        "description": desc[:1200],
        "ok": bool(desc),
    }
    _log(row)
    return row


def prompt_block(row: dict[str, Any], *, cap: int = 700) -> str:
    if not row.get("ok"):
        return "Vision: could not read this image (moondream miss)."
    folder = str(row.get("folder") or "unsorted")
    desc = str(row.get("description") or "")[:500]
    sorted_to = str(row.get("sorted") or "")
    return (
        "Vision card (moondream facts — quote these; do not invent beyond this):\n"
        f"Sorted folder: {folder}\n"
        f"What it shows: {desc}\n"
        f"Saved under: {sorted_to}\n"
        "Reply in your persona with a short take on the image. "
        "Ava: visitor-facing what it is. Bruce: ops/context. Carly: safety/privacy if relevant. "
        "Do not re-describe every detail if a teammate already did."
    )[:cap]


def handle_telegram_photo(
    cfg: Config,
    message: dict[str, Any],
    *,
    chat_id: int | str | None = None,
) -> dict[str, Any] | None:
    """Download → moondream → sort. Returns analyze row or None."""
    del chat_id
    path = download_chat_photo(cfg, message)
    if path is None:
        return None
    caption = str(message.get("caption") or "").strip()
    print(f"vision analyze path={path}", flush=True)
    row = analyze_and_sort(path, caption=caption)
    print(
        f"vision done ok={row.get('ok')} folder={row.get('folder')} "
        f"chars={len(str(row.get('description') or ''))}",
        flush=True,
    )
    return row
