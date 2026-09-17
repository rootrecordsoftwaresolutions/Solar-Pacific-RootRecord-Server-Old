"""Council self-spark + report publish. Does not wait for Cursor."""
from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any

from . import boot_brief, notify, ollama_ctl, queue, report_gate, state
from .config import CONFIG_DIR, Config

STATE_PATH = CONFIG_DIR / "autonomy.json"
INTERVAL_S = 300
MAX_SPARKS_HST = 12
TOPICS = (
    "Amend the open daily plan only. Generator-range: hybrid GENERATOR insert once per activation, not transfer.",
    "Charge-rate vs hold: do not flap Delta AC (Starlink). 400 W gate owns USB-C only. Measured watts only.",
    "Ava Ops generator badge — facts only, no fake Settings toggle.",
    "Dual-pack freshness: one live pack can still classify generator.",
    "Self-run: keep amending without the implementer. Use desk-read, report-publish, and handoff-zip. Ideas for copies+zip for manual/non-API workers. No secrets in the zip.",
    "Wrap: list what still needs owner /approve for implementer code only. Public reports are council-published.",
)


def _load() -> dict[str, Any]:
    if not STATE_PATH.is_file():
        return {}
    try:
        data = json.loads(STATE_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _save(data: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    tmp = STATE_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(STATE_PATH)


def _hst_day() -> str:
    from datetime import datetime
    from zoneinfo import ZoneInfo

    return datetime.now(ZoneInfo("Pacific/Honolulu")).strftime("%Y-%m-%d")


def publish_reports_if_any() -> dict[str, Any]:
    out = report_gate.publish_pending()
    n = int(out.get("published") or 0)
    if n:
        notify.post(
            "ava",
            f"Published {n} public report draft(s).",
        )
    return out


def maybe_spark(cfg: Config) -> dict[str, Any] | None:
    if queue.pending_count() or queue.peek():
        return None
    data = queue.load()
    if any(j.get("status") == "running" for j in data.get("jobs") or []):
        return None
    if not ollama_ctl.is_up(cfg):
        return None
    row = _load()
    day = _hst_day()
    if row.get("day") != day:
        row = {"day": day, "sparks": 0, "last_spark": 0, "topic_i": 0}
    now = int(time.time())
    last = int(row.get("last_spark") or 0)
    if last and now - last < INTERVAL_S:
        return None
    sparks = int(row.get("sparks") or 0)
    cap = int(row.get("max_sparks") or MAX_SPARKS_HST)
    if sparks >= cap:
        return None
    from . import brainstorm as _bs

    if str(_bs.status() or "idle") != "running":
        return None
    st = state.load_state(cfg.state_path)
    chat_id = str(st.get("group_chat_id") or cfg.telegram_group_chat_id or "")
    if not chat_id:
        return None
    sess_topic = _bs.topic()
    if not sess_topic:
        return None
    origin = _bs._origin_continue(wrap=False, topic=sess_topic)
    from . import proposals

    daily = proposals.open_daily(chat_id)
    pid = str((daily or {}).get("id") or "")
    job = boot_brief.enqueue_boot_round(
        cfg,
        chat_id,
        origin_text=origin,
        thread_id=f"auto-{day}-{sparks + 1}",
        extra_meta={"predecessor_id": pid} if pid else None,
    )
    if not job:
        return None
    row["sparks"] = sparks + 1
    row["last_spark"] = now
    row["last_job"] = job.get("id")
    _save(row)
    return job


def tick(cfg: Config) -> None:
    try:
        publish_reports_if_any()
    except Exception as exc:  # noqa: BLE001
        print(f"autonomy report publish skip: {type(exc).__name__}", flush=True)
    try:
        from . import brainstorm as _bs

        _bs.tick(cfg)
    except Exception as exc:  # noqa: BLE001
        print(f"brainstorm tick skip: {type(exc).__name__}", flush=True)
