"""Machine-boot and Ava Console catch-up: current-slot status files + round.

Kernel reboot (new boot id, or first start with young uptime) still posts and
queues Ava→Bruce→Carly→Ava. AVA Console start on the same boot also runs a
desk-session round. Afternoon/evening never post a leftover morning file.
"""
from __future__ import annotations

import hashlib
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo
import re

from . import ollama_ctl, prompting, queue, router, sanitize, state, telegram
from .config import Config, load_config

HST = ZoneInfo("Pacific/Honolulu")

BOOT_ID_PATH = Path("/proc/sys/kernel/random/boot_id")
UPTIME_PATH = Path("/proc/uptime")
FRESH_BOOT_S = 20 * 60

# Slot / boot status only — not 5-min / hourly churn.
CLEAN_CURRENT = (
    "morning-boot-current.md",
    "midday-boot-current.md",
    "morning-report-current.md",
    "evening-current.md",
)
SKIP_FRAGMENTS = (
    "hourly",
    "earthquake",
    "nws-weather",
    "nws-hawaii",
    "solar-weather",
    "system-performance",
    "remaining-task",
    "kilauea-current",
    "hurricane",
    "draft",
)
CLEAN_KINDS = frozenset({"morning", "midday", "evening", "boot"})
SLOT_CURRENT = {
    "morning": ("morning-boot-current.md", "morning-report-current.md"),
    "midday": ("midday-boot-current.md",),
    "evening": ("evening-current.md",),
}
DESK_ORIGIN = (
    "Read the latest chat. Answer anything a human still asked. "
    "No wake-up greeting. No console or pipeline status. "
    "First sentence is the answer."
)

BOOT_ORIGIN = (
    "Read the latest chat and the status files already in the thread. "
    "Answer anything still open. No wake-up greeting. First sentence is substance."
)


def read_boot_id() -> str:
    try:
        return BOOT_ID_PATH.read_text(encoding="utf-8").strip()
    except OSError:
        return ""


def host_uptime_s() -> float:
    try:
        return float(UPTIME_PATH.read_text(encoding="utf-8").split()[0])
    except (OSError, ValueError, IndexError):
        return 99999.0


def decide_boot_brief(
    prev_id: str,
    cur_id: str,
    uptime_s: float,
    *,
    fresh_s: float = FRESH_BOOT_S,
) -> str:
    """Return skip | arm | run."""
    prev = (prev_id or "").strip()
    cur = (cur_id or "").strip()
    if not cur:
        return "skip"
    if prev == cur:
        return "skip"
    if not prev:
        return "run" if uptime_s <= fresh_s else "arm"
    return "run"


def current_slot(now: datetime | None = None) -> str:
    hour = (now or datetime.now(HST)).hour
    if hour < 12:
        return "morning"
    if hour < 17:
        return "midday"
    return "evening"


def file_slot(kind: str, name: str) -> str:
    low = (name or "").lower()
    kind_l = (kind or "").strip().lower()
    if "midday" in low or kind_l == "midday":
        return "midday"
    if "evening" in low or kind_l == "evening":
        return "evening"
    if "morning" in low or kind_l in {"morning", "boot"}:
        return "morning"
    return kind_l


def report_matches_slot(kind: str, name: str, now: datetime | None = None) -> bool:
    return file_slot(kind, name) == current_slot(now)


def today_spoken(now: datetime | None = None) -> str:
    now = now or datetime.now(HST)
    return f"{now.strftime('%B')} {now.day}, {now.year}"


def today_iso(now: datetime | None = None) -> str:
    now = now or datetime.now(HST)
    return now.strftime("%Y-%m-%d")


_STAMP_LEAD = re.compile(
    r"^\*\*[^*]+\*\*\s*[—\-]\s*\d{4}-\d{2}-\d{2}[^\n]*\n+",
)


def report_text_is_for_today(text: str, now: datetime | None = None) -> bool:
    """Refuse archives and bodies whose only 'today' is a fresh write_current stamp."""
    now = now or datetime.now(HST)
    iso = today_iso(now)
    spoken = today_spoken(now)
    body = (text or "").strip()
    if not body:
        return False
    lead = re.match(r"^\*\*[^*]+\*\*\s*[—\-]\s*(20\d{2}-\d{2}-\d{2})", body)
    if lead and lead.group(1) != iso:
        return False
    core = _STAMP_LEAD.sub("", body, count=1)
    head = core[:1200]
    return iso in head or spoken in head


def report_is_for_today(path: Path, now: datetime | None = None) -> bool:
    """Refuse archives and leftover current pointers from another HST day."""
    now = now or datetime.now(HST)
    iso = today_iso(now)
    name = path.name
    dated = re.search(r"(20\d{2}-\d{2}-\d{2})", name)
    if dated and dated.group(1) != iso:
        return False
    try:
        head = path.read_text(encoding="utf-8", errors="replace")[:4000]
    except OSError:
        return False
    return report_text_is_for_today(head, now)


def reports_dir() -> Path:
    try:
        from apps.core import config

        return Path(config.REPORTS_DIR)
    except Exception:  # noqa: BLE001
        return Path.home() / "Media" / "public" / "documents" / "reports"


def is_clean_report_name(name: str) -> bool:
    low = (name or "").lower()
    if any(frag in low for frag in SKIP_FRAGMENTS):
        return False
    return name in CLEAN_CURRENT or low.endswith("-boot-current.md")


def select_clean_reports(root: Path | None = None, now: datetime | None = None) -> list[Path]:
    base = root or reports_dir()
    out: list[Path] = []
    seen_hash: set[str] = set()
    for name in SLOT_CURRENT[current_slot(now)]:
        path = base / name
        if not path.is_file() or path.stat().st_size < 40:
            continue
        if not is_clean_report_name(path.name):
            continue
        if not report_is_for_today(path, now):
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest in seen_hash:
            continue
        seen_hash.add(digest)
        out.append(path)
    return out


def _chat_id(st: dict[str, Any], cfg: Config) -> str:
    return str(st.get("group_chat_id") or cfg.telegram_group_chat_id or "").strip()


def _voice_for_report(name: str) -> str:
    try:
        from apps.voice.speakers import agent_for

        return agent_for(kind_for_status_file(name))
    except Exception:
        return "ava"


def kind_for_status_file(name: str) -> str:
    low = (name or "").lower()
    if "midday" in low:
        return "midday"
    if "evening" in low:
        return "evening"
    if "morning-boot" in low or low.endswith("-boot-current.md"):
        return "boot"
    if "morning" in low:
        return "morning"
    return "boot"


def current_audio_for(kind: str, name: str = "") -> Path | None:
    try:
        from apps.core import config as _cfg
    except Exception:
        return None
    low = (name or "").lower()
    extras: list[str] = []
    if kind in {"boot", "morning"} or "morning-boot" in low:
        extras.extend(["morning-boot-current.wav", "boot-report-current.wav"])
    if kind == "midday" or "midday-boot" in low:
        extras.extend(["midday-boot-current.wav", "midday-report-current.wav"])
    names: list[str] = []
    for n in (f"{kind}-report-current.wav", f"{kind}-report-current.mp3", *extras):
        if n and n not in names:
            names.append(n)
    for n in names:
        cand = _cfg.GENERATED_DIR / n
        if cand.is_file() and cand.stat().st_size > 0:
            return cand
    return None


def _send_text(cfg: Config, voice: str, chat_id: str, text: str) -> None:
    body = sanitize.sanitize_outbound((text or "").strip(), voice=voice)
    if not body:
        return
    telegram.send_message(cfg.token_for(voice), chat_id, body)


def _send_file(cfg: Config, voice: str, chat_id: str, path: Path, caption: str) -> None:
    if not report_is_for_today(path):
        print(f"boot-brief skip stale file {path.name}", flush=True)
        return
    if current_slot() != "morning" and "morning" in path.name.lower():
        return
    cap = sanitize.sanitize_outbound(caption, voice=voice)[:900]
    if current_slot() != "morning" and "morning" in cap.lower():
        cap = sanitize.sanitize_outbound(f"{current_slot()} status")[:900]
    telegram.send_document(cfg.token_for("bruce"), chat_id, path, caption=cap or None)


def notify_generated_report(kind: str, path: Path) -> bool:
    """Post a newly written morning/evening file. Boot status is catch-up only."""
    kind = (kind or "").strip().lower()
    name = path.name.lower() if path else ""
    if kind == "boot" or name.endswith("-boot-current.md"):
        return False
    if kind not in CLEAN_KINDS:
        return False
    if not path.is_file() or not is_clean_report_name(path.name):
        return False
    if not report_matches_slot(kind, path.name):
        return False
    if not report_is_for_today(path):
        return False
    cfg = load_config()
    st = state.load_state(cfg.state_path)
    if not st.get("boot_brief_completed"):
        return False
    chat_id = _chat_id(st, cfg)
    if not chat_id:
        return False
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return False
    audio = current_audio_for(kind, path.name)
    from . import report_cast

    out = report_cast.notify_report(kind, transcript=text, audio=audio, title=f"{file_slot(kind, path.name)} status")
    return bool(out.get("ok")) and not out.get("skipped")


def enqueue_boot_round(
    cfg: Config,
    chat_id: str,
    *,
    origin_text: str | None = None,
    thread_id: str = "boot-brief",
    extra_meta: dict[str, Any] | None = None,
    prompt: str | None = None,
) -> dict[str, Any] | None:
    origin = origin_text or BOOT_ORIGIN
    order = list(router.ROUND_ORDER)
    speak = prompt or prompting.build_speak_prompt(
        speaker_line="Desk catch-up (no human in this turn).",
        display="desk",
        voice="ava",
        user_text=origin,
        chat_id=chat_id,
        extra="",
        round_open=True,
        catch_up=True,
    )
    meta: dict[str, Any] = {
        "allow_loop": True,
        "round": True,
        "round_order": order,
        "round_index": 0,
        "origin_text": origin,
    }
    if extra_meta:
        meta.update(extra_meta)
    return queue.enqueue(
        voice="ava",
        prompt=speak,
        chat_id=chat_id,
        reply_to=None,
        source_message_id=None,
        thread_id=thread_id,
        depth=0,
        kind="speak",
        meta=meta,
    )


def _origin_session_id() -> str:
    try:
        from apps.core.services.origin_session import read_id

        return read_id()
    except Exception:  # noqa: BLE001
        return ""


def _mark_origin_seen(st: dict[str, Any]) -> None:
    oid = _origin_session_id()
    if oid:
        st["last_origin_session_id"] = oid


def _post_status_and_round(
    cfg: Config,
    st: dict[str, Any],
    *,
    intro: str,
    origin_text: str,
    thread_id: str,
) -> str:
    try:
        return _post_status_and_round_inner(
            cfg, st, intro=intro, origin_text=origin_text, thread_id=thread_id
        )
    except Exception as exc:  # noqa: BLE001
        print(f"boot-brief failed: {type(exc).__name__}", flush=True)
        return "fail"


def _post_status_and_round_inner(
    cfg: Config,
    st: dict[str, Any],
    *,
    intro: str,
    origin_text: str,
    thread_id: str,
) -> str:
    chat_id = _chat_id(st, cfg)
    if not chat_id:
        print("boot-brief: no group chat id — skipped posts", flush=True)
        return "no-chat"
    if intro:
        _send_text(cfg, "ava", chat_id, intro)
    slot = current_slot()
    for path in select_clean_reports():
        kind = kind_for_status_file(path.name)
        wav = current_audio_for(kind, path.name)
        voice = _voice_for_report(path.name)
        if wav is not None and cfg.token_for(voice):
            cap = sanitize.sanitize_outbound(f"{slot} status", voice=voice)[:900]
            telegram.send_audio(cfg.token_for(voice), chat_id, wav, caption=cap)
        _send_file(
            cfg,
            _voice_for_report(path.name),
            chat_id,
            path,
            caption=f"{slot} status",
        )
    if ollama_ctl.is_up(cfg):
        job = enqueue_boot_round(
            cfg, chat_id, origin_text=origin_text, thread_id=thread_id
        )
        print(f"boot-brief queued round job={job and job.get('id')}", flush=True)
    else:
        _send_text(
            cfg,
            "bruce",
            chat_id,
            "On-device brain is still down. Files are up; we'll talk when it wakes.",
        )
    st["boot_brief_completed"] = True
    _mark_origin_seen(st)
    state.save_state(st)
    return "run"


def maybe_run(cfg: Config, st: dict[str, Any]) -> str:
    """Arm, skip, or run. Never raises into the poll loop."""
    cur = read_boot_id()
    prev = str(st.get("last_boot_brief_id") or "")
    action = decide_boot_brief(prev, cur, host_uptime_s())
    if action == "skip":
        return maybe_desk_session(cfg, st)
    if action == "arm":
        st["last_boot_brief_id"] = cur
        st["boot_brief_completed"] = False
        state.save_state(st)
        print("boot-brief armed (will run on next machine reboot)", flush=True)
        return maybe_desk_session(cfg, st)

    st["last_boot_brief_id"] = cur
    st["boot_brief_completed"] = True
    _mark_origin_seen(st)
    state.save_state(st)
    print("boot-brief: machine boot noted — no Telegram round", flush=True)
    return "silent"


def maybe_desk_session(cfg: Config, st: dict[str, Any]) -> str:
    """Origin recycle: remember the session. Do not post or queue a round."""
    oid = _origin_session_id()
    if not oid:
        return "no-origin"
    if str(st.get("last_origin_session_id") or "") == oid:
        return "skip"
    _mark_origin_seen(st)
    state.save_state(st)
    print("desk-session: origin id stamped — no Telegram round", flush=True)
    return "silent"
