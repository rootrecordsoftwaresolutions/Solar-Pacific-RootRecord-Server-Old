"""Timed council brainstorm. No Cursor. Owner sets topic + duration; stop wraps early."""
from __future__ import annotations

import json
import re
import time
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

from . import boot_brief, notify, ollama_ctl, queue, state
from .config import CONFIG_DIR, Config

HST = ZoneInfo("Pacific/Honolulu")
PATH = CONFIG_DIR / "brainstorm.json"
MIN_S = 10 * 60
INTERVAL_S = 5 * 60
ASK_IDLE_S = 20 * 60
SAID_KEEP = 14
ANGLES = (
    "Name one concrete problem or visitor-facing outcome. Do not restate the topic.",
    "Offer one new option: a screen, rule, visitor sentence, or file. Not already said.",
    "Feasibility on this PC: one constraint, cost, or refuse. Not already said.",
    "Pick: keep one idea, drop one. One sentence why.",
    "Sharpen: one unstated edge or metric. Or PASS.",
)

START_PHRASES = (
    "/brainstorm",
    "brainstorm",
    "let's brainstorm",
    "lets brainstorm",
    "start brainstorming",
    "start a brainstorm",
    "thought session",
)
STOP_RE = re.compile(
    r"(?i)(?<!talking )\b(stop|wrap up|that's enough|thats enough|end brainstorm|stop brainstorming)\b"
)


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {"status": "idle"}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"status": "idle"}
    return data if isinstance(data, dict) else {"status": "idle"}


def _save(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    tmp = PATH.with_suffix(".tmp")
    data = dict(data)
    data["updated"] = int(time.time())
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def status() -> str:
    return str(_load().get("status") or "idle")


def topic() -> str:
    return str(_load().get("topic") or "").strip()[:400]


def round_n() -> int:
    return max(1, int(_load().get("round") or 1))


def _clip_said(text: str, n: int = 160) -> str:
    s = re.sub(r"\s+", " ", (text or "").strip())
    if len(s) <= n:
        return s
    return s[: n - 1] + "…"


def note_said(voice: str, text: str) -> None:
    """Keep a short unique list so later rounds can PASS instead of looping."""
    t = re.sub(r"<<<[^>]+>>>", "", text or "")
    t = re.sub(r"[.\s]+$", "", t.strip())
    t = re.sub(r"\s+", " ", t)
    if t.upper() in {"PASS", "NO ADD", "NOTHING TO ADD", "SKIP", ""}:
        return
    line = _clip_said(text)
    if not line:
        return
    sess = _load()
    if str(sess.get("status") or "idle") not in {"running", "wrapping"}:
        return
    rows = [str(x) for x in (sess.get("said") or []) if str(x).strip()]
    key = line.lower()
    if any(key == r.lower() or key in r.lower() or r.lower() in key for r in rows):
        return
    who = (voice or "?").strip().lower()[:8]
    rows.append(f"{who}: {line}")
    sess["said"] = rows[-SAID_KEEP:]
    _save(sess)


def claimed_block(*, cap: int = 900) -> str:
    rows = [str(x) for x in (_load().get("said") or []) if str(x).strip()]
    if not rows:
        return "Already said: (none)."
    blob = "Already said (do not repeat; PASS if you have nothing new):\n- " + "\n- ".join(rows)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def _angle(n: int, *, wrap: bool) -> str:
    if wrap:
        return (
            "Conclude: at most three bullets of decisions from this session. "
            "Do not paste Already said verbatim. No leftover plans from other days."
        )
    return ANGLES[(max(1, int(n)) - 1) % len(ANGLES)]


def _round_meta(*, wrap: bool, pid: str = "") -> dict[str, Any]:
    extra: dict[str, Any] = {
        "brainstorm_wrap": wrap,
        "allow_pass": True,
        "no_propose": not wrap,
    }
    if pid:
        extra["predecessor_id"] = pid
    return extra


def parse_duration_s(text: str) -> tuple[int, bool] | None:
    """(seconds, bumped_to_minimum) or None if no duration found."""
    raw = (text or "").strip().lower()
    if not raw:
        return None
    raw = raw.replace("a half hour", "30 minutes").replace("half an hour", "30 minutes")
    raw = raw.replace("an hour", "1 hour").replace("one hour", "1 hour")
    asked: int | None = None
    m = re.search(r"(\d+(?:\.\d+)?)\s*(h|hr|hrs|hour|hours)\b", raw)
    if m:
        asked = int(float(m.group(1)) * 3600)
    if asked is None:
        m = re.search(r"(\d+)\s*(m|min|mins|minute|minutes)\b", raw)
        if m:
            asked = int(m.group(1)) * 60
    if asked is None:
        m = re.search(r"^/brainstorm(?:@[a-z0-9_]+)?\s+(\d+)\b", raw)
        if m:
            asked = int(m.group(1)) * 60
    if asked is None and re.fullmatch(r"\d+", raw):
        asked = int(raw) * 60
    if asked is None:
        return None
    return max(MIN_S, asked), asked < MIN_S


_DURATION_STRIP = re.compile(
    r"(?i)(?:^|\s)(?:\d+(?:\.\d+)?\s*(?:h|hr|hrs|hour|hours|m|min|mins|minute|minutes)|"
    r"a half hour|half an hour|an hour|one hour)\b"
)


def parse_topic(text: str) -> str:
    """Human topic leftover after stripping /brainstorm and duration phrases."""
    raw = (text or "").strip()
    if not raw:
        return ""
    had_cmd = bool(re.match(r"^/brainstorm(?:@[A-Za-z0-9_]+)?\b", raw, flags=re.I))
    raw = re.sub(r"^/brainstorm(?:@[A-Za-z0-9_]+)?\s*", "", raw, flags=re.I)
    if had_cmd:
        raw = re.sub(r"^\d+\s+", "", raw)
    low = raw.lower()
    for p in START_PHRASES:
        if p == "/brainstorm":
            continue
        if low == p or low.startswith(p + " "):
            raw = raw[len(p) :].strip()
            break
    raw = _DURATION_STRIP.sub(" ", raw)
    raw = re.sub(r"\s+", " ", raw).strip(" ,.-")
    raw = re.sub(r"(?i)^(for|about|on|regarding|re)\s+", "", raw).strip()
    raw = re.sub(r"(?i)\s+(for|about|on|regarding|re)$", "", raw).strip()
    if raw.lower() in {"", "brainstorm", "please", "now", "go"}:
        return ""
    return raw[:400]


def _looks_start(text: str) -> bool:
    low = (text or "").strip().lower()
    if low.startswith("/brainstorm") and "stop" not in low:
        return True
    return any(low == p or low.startswith(p + " ") for p in START_PHRASES if p != "/brainstorm")


def _looks_stop(text: str) -> bool:
    low = (text or "").strip().lower()
    if low in {"stop talking", "hold off", "holdoff", "quiet"}:
        return False
    if low in {"/brainstorm stop", "/stop brainstorm", "stop brainstorm"}:
        return True
    if low == "stop":
        return True
    return bool(STOP_RE.search(low))


def _send_wrap_zip(cfg: Config, chat_id: str) -> None:
    try:
        from .handoff import send_to_chat

        send_to_chat(cfg, chat_id)
    except Exception as exc:  # noqa: BLE001
        print(f"brainstorm zip skip: {type(exc).__name__}", flush=True)


def _end_hst(ts: int) -> str:
    return datetime.fromtimestamp(int(ts), HST).strftime("%-I:%M %p HST")


def _origin_continue(*, wrap: bool, topic: str = "", n: int = 1) -> str:
    topic = (topic or "").strip()
    focus = (
        f"Stay on this topic only: {topic}."
        if topic
        else "Stay on the human's topic only."
    )
    lanes = (
        " Each voice adds one new useful point in-lane, or the single word PASS."
        " Useful means a named feature, visitor sentence, rule, file, or constraint — not 'we should align'."
        " If the last speaker left the topic, do not follow them."
        " If Already said already has your point, PASS."
        " Do not copy each other."
        " No weather, volcano, EcoFlow, host, CPU, or RAM unless that is the topic."
        " No invented numbers. No leftover daily-plan items."
    )
    job = _angle(n, wrap=wrap)
    if wrap:
        return (
            f"WRAP now. {focus} {job} "
            "AMEND the open daily plan only with this topic. Do not /done. Do not start new topics."
            f"{lanes} "
            "Bruce files the amendment and the proposals zip. Owner /approve is implementer only."
        )
    opener = " Ava opens with one useful point; later voices may PASS." if int(n) == 1 else ""
    return (
        f"Timed brainstorm. Implementer stays out. {focus} This round: {job}{opener} Do not /done."
        f"{lanes} Bruce files only at wrap."
    )


def _enqueue_round(cfg: Config, chat_id: str, *, wrap: bool, n: int, topic: str = "") -> dict[str, Any] | None:
    from . import prompting, proposals

    daily = proposals.open_daily(chat_id)
    pid = str((daily or {}).get("id") or "")
    thread = f"brainstorm-wrap-{n}" if wrap else f"brainstorm-{n}"
    origin = _origin_continue(wrap=wrap, topic=topic, n=n)
    prompt = prompting.build_brainstorm_open_prompt(
        voice="ava",
        origin_text=origin,
        chat_id=chat_id,
    )
    return boot_brief.enqueue_boot_round(
        cfg,
        chat_id,
        origin_text=origin,
        thread_id=thread,
        extra_meta=_round_meta(wrap=wrap, pid=pid),
        prompt=prompt,
    )


def _ask_prompt(*, have_topic: bool, have_duration: bool) -> str:
    bits: list[str] = []
    if not have_topic:
        bits.append("What should we think about?")
    if not have_duration:
        bits.append("How long? Minimum 10 minutes. No maximum.")
    bits.append(
        "We conclude when time is up. Say stop to cancel. We read the desk files ourselves."
    )
    return " ".join(bits)


def _ask(
    cfg: Config,
    chat_id: str,
    *,
    topic: str = "",
    duration_s: int | None = None,
    bumped: bool = False,
) -> None:
    notify.post(
        "ava",
        _ask_prompt(have_topic=bool(topic.strip()), have_duration=duration_s is not None),
    )
    row: dict[str, Any] = {
        "status": "asking",
        "chat_id": str(chat_id),
        "asked_at": int(time.time()),
        "topic": topic.strip(),
    }
    if duration_s is not None:
        row["duration_s"] = int(duration_s)
        row["bumped"] = bool(bumped)
    _save(row)


def _begin(cfg: Config, chat_id: str, duration_s: int, *, bumped: bool, topic: str = "") -> None:
    now = int(time.time())
    ends = now + int(duration_s)
    mins = max(10, int(duration_s) // 60)
    extra = " (raised to the 10-minute minimum)." if bumped else ""
    focus = f" Topic: {topic}." if topic.strip() else ""
    from . import ollama_ctl

    npu = ollama_ctl.flm_start()
    npu_bit = " NPU mapped." if npu else " NPU missed — 3B on Ollama."
    notify.post(
        "ava",
        f"Brainstorming for {mins} minutes{extra}.{focus}{npu_bit} We wrap by {_end_hst(ends)}. "
        "Say stop in chat to wrap early.",
    )
    job = _enqueue_round(cfg, str(chat_id), wrap=False, n=1, topic=topic)
    _save(
        {
            "status": "running",
            "chat_id": str(chat_id),
            "started": now,
            "ends": ends,
            "duration_s": int(duration_s),
            "topic": topic.strip(),
            "said": [],
            "last_spark": now,
            "round": 1 if job else 0,
            "wrap_enqueued": False,
        }
    )


def _try_start(cfg: Config, chat_id: str, text: str, sess: dict[str, Any] | None = None) -> bool:
    """Merge topic/duration from this message + asking session. Always consumes."""
    sess = sess or {}
    topic = str(sess.get("topic") or "").strip() or parse_topic(text)
    parsed = parse_duration_s(text)
    if parsed:
        dur: int | None = int(parsed[0])
        bumped = bool(parsed[1])
    elif sess.get("duration_s") is not None:
        dur = int(sess["duration_s"])
        bumped = bool(sess.get("bumped"))
    else:
        dur = None
        bumped = False
    if topic and dur is not None:
        _begin(cfg, str(chat_id), dur, bumped=bumped, topic=topic)
        return True
    _ask(cfg, str(chat_id), topic=topic, duration_s=dur, bumped=bumped)
    return True


def wrap_now(cfg: Config, reason: str = "owner wrap") -> bool:
    """Conclude the current thought session even if no timer was armed."""
    sess = _load()
    st = str(sess.get("status") or "idle")
    if st in {"running", "asking"}:
        return request_wrap(cfg, reason)
    loaded = state.load_state(cfg.state_path)
    chat_id = str(sess.get("chat_id") or loaded.get("group_chat_id") or cfg.telegram_group_chat_id or "")
    if not chat_id:
        return False
    notify.post("ava", "Wrapping the brainstorm now. We'll conclude this round.")
    _save(
        {
            "status": "wrapping",
            "chat_id": chat_id,
            "wrap_enqueued": False,
            "round": int(sess.get("round") or 0),
            "stop_reason": reason,
        }
    )
    return True


def request_wrap(cfg: Config, reason: str = "stop") -> bool:
    sess = _load()
    if str(sess.get("status") or "idle") not in {"running", "asking"}:
        if str(sess.get("status") or "") == "wrapping":
            return True
        return False
    chat_id = str(sess.get("chat_id") or "")
    if not chat_id:
        st = state.load_state(cfg.state_path)
        chat_id = str(st.get("group_chat_id") or cfg.telegram_group_chat_id or "")
    notify.post("ava", "Wrapping the brainstorm now. We'll conclude this round.")
    sess["status"] = "wrapping"
    sess["stop_reason"] = reason
    sess["wrap_enqueued"] = False
    _save(sess)
    try:
        from . import queue as _q

        _q.drop_brainstorm_continue_jobs()
    except Exception:
        pass
    return True


def handle_owner_text(cfg: Config, chat_id: int | str, text: str) -> bool:
    """True if the message was consumed as brainstorm control."""
    raw = (text or "").strip()
    low = raw.lower()
    sess = _load()
    st = str(sess.get("status") or "idle")

    if _looks_stop(raw) and st in {"running", "asking", "wrapping"}:
        if st == "asking":
            notify.post("ava", "Brainstorm cancelled.")
            _save({"status": "idle"})
            return True
        request_wrap(cfg, "stop")
        return True

    if st == "asking":
        return _try_start(cfg, chat_id, raw, sess)

    if _looks_start(raw):
        if st in {"running", "wrapping"}:
            notify.post("ava", "Already in a brainstorm. Say stop to wrap.")
            return True
        return _try_start(cfg, chat_id, raw)

    return False


def _finish_idle(now: int) -> None:
    from . import ollama_ctl

    ollama_ctl.flm_stop()
    _save({"status": "idle", "last_finished": now, "zip_sent": True})


def _queue_idle() -> bool:
    if queue.pending_count() or queue.peek():
        return False
    data = queue.load()
    return not any(j.get("status") == "running" for j in data.get("jobs") or [])


def _idle_ok(cfg: Config) -> bool:
    from . import ollama_ctl

    brain = ollama_ctl.flm_is_up() or ollama_ctl.is_up(cfg)
    return _queue_idle() and brain


def tick(cfg: Config) -> None:
    sess = _load()
    st = str(sess.get("status") or "idle")
    now = int(time.time())
    if st == "asking":
        asked = int(sess.get("asked_at") or 0)
        if asked and now - asked > ASK_IDLE_S:
            _save({"status": "idle"})
        return
    chat_id = str(sess.get("chat_id") or "")
    if not chat_id:
        loaded = state.load_state(cfg.state_path)
        chat_id = str(loaded.get("group_chat_id") or cfg.telegram_group_chat_id or "")
        sess["chat_id"] = chat_id
    if st == "running":
        ends = int(sess.get("ends") or 0)
        if ends and now >= ends:
            request_wrap(cfg, "time")
            sess = _load()
            st = str(sess.get("status") or "idle")
        elif _idle_ok(cfg):
            last = int(sess.get("last_spark") or 0)
            if last and now - last >= INTERVAL_S:
                n = int(sess.get("round") or 0) + 1
                job = _enqueue_round(
                    cfg, chat_id, wrap=False, n=n, topic=str(sess.get("topic") or "")
                )
                if job:
                    sess["round"] = n
                    sess["last_spark"] = now
                    sess["last_job"] = job.get("id")
                    _save(sess)
    if str(_load().get("status") or "") != "wrapping":
        return
    sess = _load()
    cid = str(sess.get("chat_id") or chat_id)
    if not sess.get("wrap_enqueued"):
        if _idle_ok(cfg):
            n = int(sess.get("round") or 0) + 1
            job = _enqueue_round(
                cfg, cid, wrap=True, n=n, topic=str(sess.get("topic") or "")
            )
            if job:
                sess["wrap_enqueued"] = True
                sess["last_spark"] = now
                sess["last_job"] = job.get("id")
                _save(sess)
            return
        if _queue_idle():
            _send_wrap_zip(cfg, cid)
            _finish_idle(now)
        return
    if not _queue_idle():
        return
    if not sess.get("zip_sent"):
        _send_wrap_zip(cfg, cid)
    _finish_idle(now)
