"""Council vision — Ollama VLM (qwen2.5vl), verify pass, sort, Ava edit-via-reply."""
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
TAKES_PATH = CONFIG_DIR / "vision-takes.json"

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
    "Say the setting. Do not invent brand names you cannot read. "
    "Never output coordinate lists, bounding boxes, or lines like ids: [numbers]."
)

VERIFY_PROMPT = (
    "Second pass — read sale tags and price stickers only. "
    "Copy every dollar amount and discount phrase exactly as printed "
    "(examples: $10 OFF, $10 off, Save $2, 2 for $5, $1.29). "
    "Prefer large sale text like OFF / SALE over tiny shelf digits if both appear. "
    "If a digit is hard to read, write Unclear — do not guess. "
    "Do not describe the products or shelf. Numbers and discount words only. "
    "Never output coordinate lists or ids: [numbers]."
)

# Moondream sometimes dumps detector coords instead of language — treat as a miss.
_IDS_JUNK = re.compile(
    r"^\s*ids?\s*:\s*\[|\bbounding\s*box\b|\bcoord(inate)?s?\b",
    re.IGNORECASE,
)


def _clean_desc(raw: str | None) -> str:
    text = re.sub(r"\s+", " ", (raw or "")).strip()
    if not text:
        return ""
    if _IDS_JUNK.search(text) or re.fullmatch(r"[\d\.\,\s\[\];:idsIDSboxBOX\-]+", text):
        return ""
    # Reject tiny numeric-only blobs
    if len(text) < 16 and re.search(r"\d", text) and not re.search(r"[A-Za-z]{3,}", text):
        return ""
    return text


CORRECT_FOOTER = "Reply to this message if a price or detail is wrong — I'll edit it."


def _now() -> int:
    return int(time.time())


def _log(row: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a", encoding="utf-8") as f:
        f.write(json.dumps(row, sort_keys=True) + "\n")


def _load_takes() -> dict[str, Any]:
    if not TAKES_PATH.is_file():
        return {"takes": {}}
    try:
        data = json.loads(TAKES_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"takes": {}}
    takes = data.get("takes")
    if not isinstance(takes, dict):
        takes = {}
    data["takes"] = takes
    return data


def _save_takes(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    tmp = TAKES_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(TAKES_PATH)


def take_key(chat_id: int | str, message_id: int | str) -> str:
    return f"{chat_id}:{message_id}"


def register_take(
    *,
    chat_id: int | str,
    message_id: int,
    vision_row: dict[str, Any],
    body: str,
    photo_message_id: int | None = None,
    from_user_id: int | str | None = None,
) -> None:
    data = _load_takes()
    takes = data["takes"]
    # Drop takes older than 48h
    cutoff = _now() - 48 * 3600
    takes = {
        k: v
        for k, v in takes.items()
        if isinstance(v, dict) and int(v.get("ts") or 0) >= cutoff
    }
    takes[take_key(chat_id, message_id)] = {
        "ts": _now(),
        "chat_id": str(chat_id),
        "message_id": int(message_id),
        "photo_message_id": int(photo_message_id) if photo_message_id is not None else None,
        "from_user_id": str(from_user_id) if from_user_id is not None else "",
        "body": (body or "")[:3500],
        "vision": {
            "src": vision_row.get("src"),
            "sorted": vision_row.get("sorted"),
            "folder": vision_row.get("folder"),
            "description": str(vision_row.get("description") or "")[:1200],
            "verified": str(vision_row.get("verified") or "")[:600],
            "caption": str(vision_row.get("caption") or "")[:200],
        },
        "corrections": [],
    }
    data["takes"] = takes
    _save_takes(data)


def lookup_take(chat_id: int | str, message_id: int | str | None) -> dict[str, Any] | None:
    if message_id is None:
        return None
    data = _load_takes()
    row = data["takes"].get(take_key(chat_id, message_id))
    return dict(row) if isinstance(row, dict) else None


def pick_folder(description: str) -> str:
    low = (description or "").lower()
    for name, kws in SORT_RULES:
        if any(k in low for k in kws):
            return name
    return "unsorted"


def look(path: Path, *, prompt: str = PROMPT) -> str | None:
    """One-shot vision model via Ollama. keep_alive 0 so NPU chat stays free."""
    from apps.core.services import ollama as oc

    return oc.look_sync(prompt, [path], timeout=120)


def verify_numbers(path: Path) -> str:
    """Second vision pass — prices/discounts only. Empty on miss."""
    raw = look(path, prompt=VERIFY_PROMPT) or ""
    return re.sub(r"\s+", " ", raw).strip()[:600]


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
    dest = _unique_path(dest_dir / src.name)
    if dest.resolve() != src.resolve():
        shutil.copy2(src, dest)
    return dest


def filename_from_description(
    description: str,
    *,
    ext: str = ".jpg",
    caption: str = "",
) -> str:
    """Build a short filesystem-safe name from the vision description."""
    raw = (description or "").split("Verified text/prices:")[0].strip()
    if _IDS_JUNK.search(raw):
        raw = ""
    for sep in (". ", "! ", "? ", "\n"):
        if sep in raw:
            raw = raw.split(sep, 1)[0].strip()
            break
    cap = (caption or "").strip()
    if cap and (not raw or len(raw) < 12):
        raw = f"{cap} {raw}".strip()
    # Keep letters/digits/spaces/hyphens; drop the rest.
    slug = re.sub(r"[^\w\s\-]+", "", raw, flags=re.UNICODE)
    slug = re.sub(r"[\s_]+", "-", slug.strip()).strip("-").lower()
    slug = re.sub(r"-{2,}", "-", slug)
    if not slug or slug.startswith("ids-") or re.fullmatch(r"[\d\-\.]+", slug):
        slug = "photo"
    # Avoid giant names from hallucinated price lists.
    parts = [p for p in slug.split("-") if p and p not in {"a", "an", "the", "of", "and", "in", "on"}]
    slug = "-".join(parts[:12])[:72].strip("-") or "photo"
    if slug.startswith("ids") or not re.search(r"[a-z]", slug):
        slug = "photo"
    if not ext.startswith("."):
        ext = f".{ext}"
    if ext.lower() not in {".jpg", ".jpeg", ".png", ".webp", ".gif"}:
        ext = ".jpg"
    return f"{slug}{ext.lower()}"


def _unique_path(dest: Path) -> Path:
    if not dest.exists():
        return dest
    stem, suf = dest.stem, dest.suffix
    for i in range(2, 100):
        cand = dest.with_name(f"{stem}-{i}{suf}")
        if not cand.exists():
            return cand
    return dest.with_name(f"{stem}-{_now()}{suf}")


def rename_to_description(path: Path, description: str, *, caption: str = "") -> Path:
    """Rename a downloaded inbox file to match the vision description."""
    if not path.is_file():
        return path
    ext = path.suffix.lower() or ".jpg"
    name = filename_from_description(description, ext=ext, caption=caption)
    dest = _unique_path(path.with_name(name))
    if dest.resolve() == path.resolve():
        return path
    try:
        path.rename(dest)
        print(f"vision renamed {path.name} → {dest.name}", flush=True)
        return dest
    except OSError as exc:
        print(f"vision rename fail {exc}", flush=True)
        return path


def analyze_and_sort(path: Path, *, caption: str = "") -> dict[str, Any]:
    desc = _clean_desc(look(path))
    if not desc and path.is_file():
        # One retry with a stricter plain-English ask.
        desc = _clean_desc(
            look(
                path,
                prompt=(
                    "In plain English only: what is in this photo? "
                    "People, objects, place. No lists of numbers. No ids."
                ),
            )
        )
    verified = ""
    if desc and path.is_file():
        print(f"vision verify pass path={path}", flush=True)
        verified = _clean_desc(verify_numbers(path))
        # verify_numbers already strips; allow price-only lines even if short
        if not verified:
            raw_v = verify_numbers(path)
            if raw_v and "$" in raw_v and not _IDS_JUNK.search(raw_v):
                verified = re.sub(r"\s+", " ", raw_v).strip()[:600]
        if verified:
            desc = f"{desc} Verified text/prices: {verified}".strip()
    # Download lands as ts-hash; rename inbox + sorted copy to the description.
    if path.is_file() and desc:
        path = rename_to_description(path, desc, caption=caption)
    name_hint = path.name.replace("-", " ").replace("_", " ")
    folder = pick_folder(f"{desc} {caption or ''} {name_hint} {verified}")
    dest = sort_copy(path, folder) if path.is_file() else path
    row = {
        "ts": _now(),
        "src": str(path),
        "sorted": str(dest),
        "folder": folder,
        "caption": (caption or "")[:200],
        "description": desc[:1200],
        "verified": verified[:600],
        "filename": path.name,
        "ok": bool(desc),
    }
    _log(row)
    return row



def prompt_block(row: dict[str, Any], *, cap: int = 900, for_voice: str = "ava") -> str:
    if not row.get("ok"):
        return "Vision: could not read this image (vision miss)."
    folder = str(row.get("folder") or "unsorted")
    desc = str(row.get("description") or "")[:500]
    verified = str(row.get("verified") or "")[:400]
    sorted_to = str(row.get("sorted") or "")
    voice = (for_voice or "ava").lower()
    if voice == "ava":
        role = (
            "This is YOUR first take before Bruce sees it. "
            "Short visitor-facing read of what the photo shows. "
            "Prefer Verified text/prices over any dollar amounts in the scene description. "
            f"End with exactly this line: {CORRECT_FOOTER}"
        )
    elif voice == "bruce":
        role = (
            "Ava already posted her take. Add a brief ops/context note only — "
            "do not re-describe the whole image. Prefer verified prices. "
            "Do not repeat her correction footer."
        )
    else:
        role = "Short safety/privacy note only if relevant. Prefer verified prices."
    parts = [
        "Vision card (facts — quote these; do not invent beyond this):",
        f"Sorted folder: {folder}",
        f"What it shows: {desc}",
    ]
    if verified:
        parts.append(f"Verified text/prices: {verified}")
    parts.append(f"Saved under: {sorted_to}")
    if row.get("filename"):
        parts.append(f"Filename: {row.get('filename')}")
    parts.append(role)
    return "\n".join(parts)[:cap]


def rewrite_with_correction(
    cfg: Config,
    *,
    previous_body: str,
    correction: str,
    vision_row: dict[str, Any] | None = None,
) -> str:
    """Use NPU chat to rewrite Ava's take with a human correction. Falls back to append."""
    from . import ollama_client, sanitize

    facts = ""
    if vision_row:
        facts = (
            f"Image facts: {str(vision_row.get('description') or '')[:400]}\n"
            f"Verified: {str(vision_row.get('verified') or '')[:300]}\n"
        )
    user = (
        "Edit your prior photo take using the human correction. "
        "Keep it short and visitor-facing. Prefer their correction for prices. "
        f"End with exactly: {CORRECT_FOOTER}\n\n"
        f"{facts}"
        f"Previous message:\n{previous_body[:1200]}\n\n"
        f"Human correction:\n{correction[:500]}\n\n"
        "Output only the edited message."
    )
    try:
        raw = ollama_client.chat(
            cfg,
            cfg.model_for("ava"),
            "You are Ava. Edit the photo take. No preamble.",
            user,
            num_predict=280,
            voice="ava",
        )
    except Exception:
        raw = None
    body = sanitize.sanitize_outbound((raw or "").strip(), voice="ava")
    if not body or len(body) < 12:
        base = (previous_body or "").replace(CORRECT_FOOTER, "").strip()
        body = f"{base}\n\n(Updated: {correction.strip()})\n\n{CORRECT_FOOTER}".strip()
    if CORRECT_FOOTER not in body:
        body = f"{body.rstrip()}\n\n{CORRECT_FOOTER}"
    return body[:3500]


def apply_correction(
    cfg: Config,
    *,
    chat_id: int | str,
    take: dict[str, Any],
    correction: str,
) -> dict[str, Any]:
    """Rewrite + edit Ava's Telegram message. Returns result dict."""
    mid = int(take.get("message_id") or 0)
    if not mid:
        return {"ok": False, "detail": "no message_id"}
    vision = take.get("vision") if isinstance(take.get("vision"), dict) else {}
    new_body = rewrite_with_correction(
        cfg,
        previous_body=str(take.get("body") or ""),
        correction=correction,
        vision_row=vision,
    )
    res = telegram.edit_message_text(
        cfg.token_for("ava"),
        chat_id,
        mid,
        new_body,
    )
    if not res.get("ok"):
        return {
            "ok": False,
            "detail": str(res.get("description") or "edit failed"),
            "body": new_body,
        }
    data = _load_takes()
    key = take_key(chat_id, mid)
    row = data["takes"].get(key)
    if isinstance(row, dict):
        fixes = row.get("corrections")
        if not isinstance(fixes, list):
            fixes = []
        fixes.append({"ts": _now(), "text": correction[:500]})
        row["corrections"] = fixes[-8:]
        row["body"] = new_body
        vis = row.get("vision") if isinstance(row.get("vision"), dict) else {}
        # Re-title files from the human correction when it's short enough.
        label = correction.strip()
        if len(label) > 80:
            label = f"{vis.get('description') or ''} {correction}".strip()
        for key_path in ("src", "sorted"):
            p = Path(str(vis.get(key_path) or ""))
            if p.is_file():
                renamed = rename_to_description(p, label, caption=correction[:60])
                vis[key_path] = str(renamed)
                if key_path == "src":
                    vis["filename"] = renamed.name
        row["vision"] = vis
        data["takes"][key] = row
        _save_takes(data)
    _log(
        {
            "ts": _now(),
            "kind": "correction",
            "chat_id": str(chat_id),
            "message_id": mid,
            "correction": correction[:500],
            "ok": True,
        }
    )
    return {"ok": True, "body": new_body, "message_id": mid}


def handle_telegram_photo(
    cfg: Config,
    message: dict[str, Any],
    *,
    chat_id: int | str | None = None,
) -> dict[str, Any] | None:
    """Download → vision → verify → sort. Returns analyze row or None."""
    del chat_id
    path = download_chat_photo(cfg, message)
    if path is None:
        return None
    caption = str(message.get("caption") or "").strip()
    print(f"vision analyze path={path}", flush=True)
    row = analyze_and_sort(path, caption=caption)
    print(
        f"vision done ok={row.get('ok')} folder={row.get('folder')} "
        f"chars={len(str(row.get('description') or ''))} "
        f"verified={len(str(row.get('verified') or ''))}",
        flush=True,
    )
    return row
