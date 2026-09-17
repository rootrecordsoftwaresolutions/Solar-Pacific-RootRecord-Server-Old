"""Operator-facing optional stacks. Defaults off until Ava Ops writes toggles."""
from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from apps.core import config

log = logging.getLogger("ava.feature_toggles")

KEYS = (
    "obs",
    "radio_on_air",
    "music_bed",
    "morning_report",
    "midday_report",
    "startup_voice",
    "cloud_report_spend",
    "companions_poller",
    "local_edge",
    "xmrig",
)

_DEFAULTS: dict[str, bool] = {key: False for key in KEYS}


def state_path() -> Path:
    return config.STATE_DIR / "feature-toggles.json"


def _bool(value: object, default: bool = False) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return bool(value)
    raw = str(value or "").strip().lower()
    if raw in {"1", "true", "yes", "on"}:
        return True
    if raw in {"0", "false", "no", "off", ""}:
        return False
    return default


def _load_raw() -> dict[str, Any]:
    path = state_path()
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}
    return data if isinstance(data, dict) else {}


def load() -> dict[str, bool]:
    """OBS and other stacks stay off until Ava Ops writes feature-toggles.json.

    AVA_ENABLE_OBS is not a scheduler input — only an operator hint elsewhere.
    """
    raw = _load_raw()
    flags = dict(_DEFAULTS)
    for key in KEYS:
        if key in raw:
            flags[key] = _bool(raw.get(key), flags[key])
    return flags


def is_on(key: str, default: bool = False) -> bool:
    if key not in KEYS:
        return default
    return bool(load().get(key, default))


def save(flags: dict[str, bool]) -> dict[str, bool]:
    path = state_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    out = {key: bool(flags.get(key, _DEFAULTS[key])) for key in KEYS}
    payload = {
        **out,
        "updated_at": datetime.now(timezone.utc).isoformat(),
    }
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    return out


def _apply_side_effects(_previous: dict[str, bool], flags: dict[str, bool]) -> None:
    from apps.core.services import radio

    radio.patch(
        on_air=flags["radio_on_air"],
        on_air_sticky=flags["radio_on_air"],
        local_playback=flags["radio_on_air"],
    )
    from apps.voice.director import set_music_bed_wanted

    set_music_bed_wanted(flags["music_bed"])
    from apps.core.services.boot_report import set_morning_automation

    set_morning_automation(flags["morning_report"], reason="ava-ops-features")
    from apps.core.services.midday_report import set_midday_automation

    set_midday_automation(flags["midday_report"], reason="ava-ops-features")
    from apps.core.services import startup_voice

    startup_voice.set_enabled(flags["startup_voice"])
    from apps.core.services import report_generation

    engine = "cloud" if flags["cloud_report_spend"] else "local"
    for kind in ("morning", "midday", "evening", "late"):
        report_generation.set_engine(kind, engine)
        report_generation.set_mp3(kind, engine)
    try:
        from apps.core.scheduler import get_scheduler

        sched = get_scheduler()
        if sched:
            sched.set_obs_jobs(flags["obs"])
    except Exception as exc:
        log.warning("OBS rotator toggle failed: %s", exc)
    prev_miner = bool(_previous.get("xmrig"))
    now_miner = bool(flags.get("xmrig"))
    if prev_miner != now_miner:
        try:
            from importlib.util import module_from_spec, spec_from_file_location

            ctl_path = Path.home() / ".ollama" / "skills" / "xmrig" / "scripts" / "xmrig_ctl.py"
            spec = spec_from_file_location("xmrig_ctl", ctl_path)
            if spec is None or spec.loader is None:
                raise FileNotFoundError(str(ctl_path))
            mod = module_from_spec(spec)
            spec.loader.exec_module(mod)
            if now_miner:
                mod.start(window=True)
            else:
                mod.stop()
        except Exception as exc:
            log.warning("xmrig toggle failed: %s", exc)


def patch(updates: dict[str, Any]) -> dict[str, bool]:
    previous = load()
    flags = dict(previous)
    for key in KEYS:
        if key in updates and updates[key] is not None:
            flags[key] = _bool(updates[key], flags[key])
    saved = save(flags)
    try:
        _apply_side_effects(previous, saved)
    except Exception as exc:
        log.warning("feature toggle side effects failed: %s", exc)
    return saved


def snapshot() -> dict[str, Any]:
    from apps.core.services.obs_presence import obs_process_running

    flags = load()
    return {
        "ok": True,
        "flags": flags,
        "obs_process_running": bool(obs_process_running(force=True)),
        "obs_launches_process": False,
        "telegram": bool(config.telegram_enabled()),
        "data_dir": str(config.DATA_DIR),
        "media_dir": str(config.MEDIA_DIR),
        "ava_home": str(config.AVA_HOME),
    }
