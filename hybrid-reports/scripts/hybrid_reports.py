"""Standalone hybrid solar report processor for the AVA workstation.

Report data lives under ``Documents/Hybrid Tracking Reports``.  AVA loads this
module through its small services adapter so the feature can remain outside
of the main application tree.
"""

from __future__ import annotations

import json
import re
import textwrap
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from apps.core import config
from apps.core.services.data_layout import (
    device_role as _device_role,
    ecoflow_dir,
    ecoflow_sn_public,
    ensure_data_layout,
)

SOLAR_NOTES_CUTOFF = "+++Automation Cut Off"
HYBRID_REPORT_ROOT = Path.home() / ".ollama" / "skills" / "hybrid-reports" / "store" / "Reports"
HYBRID_CHARGE_STATE_PATH = config.STATE_DIR / "hybrid-charge-status.json"
HYBRID_REPORT_LINE_LIMIT = 111


def _daily_report_dir(now: datetime) -> Path:
    day = now.day
    suffix = "th" if 10 <= day % 100 <= 20 else {1: "st", 2: "nd", 3: "rd"}.get(day % 10, "th")
    return HYBRID_REPORT_ROOT / f"{now:%Y}" / now.strftime("%B") / f"{now:%B} {day}{suffix}, {now:%Y}"


def _automated_lines(stamp: str, content: str) -> str:
    prefix = f"> ◇ **{stamp}** — "
    width = max(1, HYBRID_REPORT_LINE_LIMIT - len(prefix))
    lines: list[str] = []
    for paragraph in content.splitlines() or [""]:
        wrapped = textwrap.wrap(
            re.sub(r"\s+", " ", paragraph).strip(),
            width=width,
            break_long_words=False,
            break_on_hyphens=False,
        ) or [""]
        lines.extend(f"{prefix}{line}" for line in wrapped)
    return "\n".join(lines)


def _automated_section(stamp: str, title: str, items: list[tuple[str, str]]) -> str:
    lines = [f"> ◇ **{stamp}** — {title}", ""]
    for heading, detail in items:
        lines.append(f"**{heading}**  ")
        lines.extend(textwrap.wrap(
            re.sub(r"\s+", " ", detail).strip(),
            width=HYBRID_REPORT_LINE_LIMIT,
            break_long_words=False,
            break_on_hyphens=False,
        ))
        lines.append("")
    return "\n".join(lines).rstrip()


def _hybrid_report_template(now: datetime) -> str:
    date_label = f"{now:%B} {now.day}, {now:%Y}"
    return f"""# HYBRID TRACKING REPORT
## {date_label}

---

## FIELD NOTES

### Terms

- **East / West Prop** — Solar panels have a manual prop to angle them toward the sun.
- **EcoFlow capacitor singing** — A high-pitched morning voltage noise that stops when output steadies.
- **LCD display beep** — The Delta 2 display beeps when a few watts begin, before full supply.
- **Morning position** — Back panels raised to the east.
- **First wattage** — Initial LCD wattage, fluctuating from 0–15 W while panels release bursts.
- **Noon position** — Flat.
- **Afternoon position** — Back panels flat, front panel propped to the west.

### Morning Steps (Critical)

- Set morning position.
- Turn phone on before EcoFlow polling / AVA boot.

## TYPICAL POWER COSTS

| Device | Power Draw |
|---|---|
| Laptop | 67 W DC while charging; 11–25 W when full |
| Starlink | 30–60 W AC; 110 W while booting |
| Hard drive dock | 11 W DC steady |
| Ryobi battery | — W per battery |
| LCD second screen | 5–10 W DC |
| Ninebot battery | — W while charging |

## BATTERY CAPACITIES

| Battery | Usable Capacity |
|---|---|
| River 2 Pro | 600 Wh |
| Delta 2 | 900 Wh |
| Ninebot Epack | 220 Wh |
| Laptop | 59 Wh |

## MAX SOLAR INPUTS

| Unit | Max Input |
|---|---|
| River 2 Pro | 220 W |
| Delta 2 | 500 W |

---

## DAILY CONDITIONS

### Weather Forecast

> ◇ **0000** — Weather pending
+++END WEATHER FORECAST

### Kilauea Prediction

> ◇ **0000** — Kilauea status pending
+++END KILAUEA PREDICTION

## HYBRID NOTES FOR {date_label.upper()}

**Legend**
> ◆ = Manual entry
> ◇ = Automated entry

*This is a hybrid manual report.*

+++Automation Cut Off

## Daily To-Do

| Time | Task |
|---|---|
| 0500–0600 / 1930 | Raise to morning position |
| 1100 | Lower to noon (flat) position |
| 1500 | Raise front for west-facing |

---

*End of report — {date_label}*
"""


def hybrid_daily_report_path(now: datetime) -> Path:
    return _daily_report_dir(now) / f"hybrid-manual-daily-report-{now:%Y-%m-%d}.md"


def ensure_hybrid_daily_report(now: datetime) -> Path:
    target = hybrid_daily_report_path(now)
    if target.is_file():
        body = target.read_text(encoding="utf-8", errors="replace")
        legacy_empty = (
            "## DAILY CONDITIONS" not in body
            and "Daily To Do:" in body
            and "This is a HYBRID manual report." in body
            and not re.search(r"(?m)^\s*>?\s*[◇◆]\s*\*\*\d{4}", body)
        )
        if not legacy_empty:
            return target
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(_hybrid_report_template(now), encoding="utf-8", newline="\n")
    return target


def _ecoflow_roots() -> list[Path]:
    ensure_data_layout()
    return [ecoflow_dir()]


def _parse_history_at_ms(row: dict[str, Any], now_ms: int) -> int | None:
    at = int(row.get("at") or 0)
    if at <= 0:
        return None
    at_s = at / 1000.0 if at > 10_000_000_000 else float(at)
    return int(at_s * 1000) if at_s < now_ms / 10 else int(at)


def _iter_history_rows(hist: Path, *, tail: int = 800):
    for path in sorted(hist.glob("*.jsonl")):
        if path.name.endswith("-minutes.jsonl"):
            continue
        try:
            lines = path.read_text(errors="replace").splitlines()[-tail:]
        except OSError:
            continue
        device = path.stem
        if not ecoflow_sn_public(device):
            continue
        for line in lines:
            line = line.strip().strip("\x00")
            if not line.startswith("{"):
                continue
            try:
                yield device, json.loads(line)
            except (TypeError, ValueError):
                continue


def _charge_status_insert(now: datetime, report_body: str | None = None) -> str | None:
    from apps.core.host_metrics import snapshot

    row = snapshot(home=config.AVA_HOME) or {}
    plugged = row.get("battery_plugged")
    if plugged is None:
        return None
    status = "Charging" if bool(plugged) else "Battery"
    try:
        battery_pct = float(row.get("battery_pct"))
    except (TypeError, ValueError):
        battery_pct = None
    try:
        prior = json.loads(HYBRID_CHARGE_STATE_PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        prior = {}
    changed = prior.get("status") != status
    if report_body is not None:
        latest_charge = re.findall(
            r"^(?:>\d{4},|> ◇ \*\*\d{4}\*\* —) CHARGE STATUS — "
            r"(Charging|Battery) \| Host battery: ([^%|]+)%",
            report_body,
            re.M,
        )
        if latest_charge:
            changed = changed or latest_charge[-1][0] != status

    HYBRID_CHARGE_STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    HYBRID_CHARGE_STATE_PATH.write_text(
        json.dumps({"status": status, "battery_pct": battery_pct, "updated_at": now.isoformat()}) + "\n",
        encoding="utf-8",
    )
    if not changed:
        return None

    def value(key: str, suffix: str = "") -> str:
        raw = row.get(key)
        return f"{float(raw):.0f}{suffix}" if raw is not None else "n/a"

    uptime = int(row.get("uptime_s") or 0)
    return _automated_lines(now.strftime("%H%M"), (
        f"CHARGE STATUS — {status} | Host battery: {value('battery_pct', '%')} | "
        f"CPU: {value('cpu_pct', '%')} | RAM: {value('mem_pct', '%')} | "
        f"Temp: {value('temp_c', 'C')} | iGPU: {value('gpu_pct', '%')} | "
        f"NPU: {'present' if row.get('npu_present') else 'not found'} | "
        f"Uptime: {uptime // 3600}h {(uptime % 3600) // 60}m"
    ))


def _power_automation_insert(now: datetime) -> str | None:
    from apps.core.services.data_layout import ecoflow_state_dir

    state_path = ecoflow_state_dir() / "ecoflow-ac-solar-gate.json"
    if not state_path.is_file():
        return None
    try:
        state = json.loads(state_path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    if not isinstance(state, dict):
        return None
    action = state.get("last_action") or state.get("desired_ac")
    if action not in {"on", "off"}:
        return None

    input_w = state.get("last_input_w")
    total_in = state.get("last_total_in_w")
    soc = state.get("last_soc")
    status = "ON" if action == "on" else "OFF"
    river_feed = "ACTIVE" if action == "on" else "IDLE"
    input_txt = f"{float(input_w):.0f}W" if input_w is not None else "n/a"
    total_txt = f"{float(total_in):.0f}W" if total_in is not None else "n/a"
    soc_txt = f"{float(soc):.0f}%" if soc is not None else "n/a"

    prior_path = config.STATE_DIR / "hybrid-power-automation-state.json"
    try:
        prior = json.loads(prior_path.read_text(encoding="utf-8")) if prior_path.is_file() else {}
    except (OSError, ValueError):
        prior = {}
    key = f"{action}|{input_w}|{total_in}|{soc}"
    if prior.get("key") == key:
        return None
    prior_path.parent.mkdir(parents=True, exist_ok=True)
    prior_path.write_text(
        json.dumps({"key": key, "action": action, "updated_at": now.isoformat()}) + "\n",
        encoding="utf-8",
    )
    return _automated_lines(now.strftime("%H%M"), (
        f"POWER AUTOMATION — DELTA 2 USB AUTO {status} | status: {status} | "
        f"input {input_txt} | total in {total_txt} | Delta SOC {soc_txt} | River feed {river_feed}"
    ))


def _generator_insert(now: datetime) -> str | None:
    """Once per generator activation, from ecoflow-live.json. Measured watts only."""
    from apps.core.services import ecoflow_public as _pub

    live = _pub.load_live()
    if live.get("transfer"):
        return None
    prior_path = config.STATE_DIR / "hybrid-generator-state.json"
    try:
        prior = json.loads(prior_path.read_text(encoding="utf-8")) if prior_path.is_file() else {}
    except (OSError, ValueError):
        prior = {}
    on = bool(live.get("generator"))
    if not on:
        if prior.get("generator"):
            prior_path.parent.mkdir(parents=True, exist_ok=True)
            prior_path.write_text(
                json.dumps({"generator": False, "updated_at": now.isoformat()}) + "\n",
                encoding="utf-8",
            )
        return None
    if prior.get("generator"):
        return None
    delta = live.get("delta") if isinstance(live.get("delta"), dict) else {}
    ac = delta.get("ac_in_w")
    ac_txt = f"{float(ac):.0f} W" if isinstance(ac, (int, float)) else "No data"
    prior_path.parent.mkdir(parents=True, exist_ok=True)
    prior_path.write_text(
        json.dumps({"generator": True, "updated_at": now.isoformat(), "ac_in_w": ac}) + "\n",
        encoding="utf-8",
    )
    return _automated_lines(
        now.strftime("%H%M"),
        f"GENERATOR — external AC-in {ac_txt} (not Delta↔River transfer)",
    )


def _strip_legacy_automation_lines(body: str) -> str:
    legacy = re.compile(
        r"(?m)^(?:#?\d{4},\s*(?:WEATHER|CHARGE STATUS|POWER AUTOMATION)|"
        r"#?\d{4},\s*(?:AUTO ECOFLOW STATUS|ECOFLOW STATUS)|"
        r"AUTO \d{4},\s*(?:ECOFLOW STATUS|POWER AUTOMATION)|"
        r">\d{4},\s*(?:WEATHER|CHARGE STATUS|POWER AUTOMATION|AUTO ECOFLOW STATUS)|"
        r">\d{4},\s*(?:ECOFLOW STATUS|POWER AUTOMATION))\s*.*$\n?"
    )
    return legacy.sub("", body).strip("\n")


def _append_report_inserts(body: str, inserts: list[str]) -> tuple[str, bool]:
    unique = [line.rstrip("\r\n") for line in inserts if line.rstrip("\r\n") not in body]
    unique = [line for line in unique if line]
    if not unique:
        return body, False

    cutoff_at = body.index(SOLAR_NOTES_CUTOFF)
    before_cutoff = body[:cutoff_at]
    if before_cutoff and not before_cutoff.endswith("\n"):
        before_cutoff += "\n"
    for line in unique:
        stamp_match = re.match(r"^> ◇ \*\*(\d{4})\*\* —", line)
        if not stamp_match:
            before_cutoff += f"{line}\n\n"
            continue
        stamp = int(stamp_match.group(1))
        lines = before_cutoff.splitlines(keepends=True)
        offset = len(before_cutoff)
        for index, existing in enumerate(lines):
            existing_match = re.match(r"^> ◇ \*\*(\d{4})\*\* —", existing)
            if existing_match and int(existing_match.group(1)) > stamp:
                offset = sum(len(item) for item in lines[:index])
                break
        before_cutoff = before_cutoff[:offset] + f"{line}\n\n" + before_cutoff[offset:]
    return before_cutoff + body[cutoff_at:], True


def append_hybrid_lifecycle_event(event: str, now: datetime | None = None) -> dict:
    from zoneinfo import ZoneInfo

    now = now or datetime.now(ZoneInfo("Pacific/Honolulu"))
    target = hybrid_daily_report_path(now)
    if not target.is_file():
        return {"ok": False, "detail": "hybrid_report_missing", "path": str(target)}
    body = target.read_text(encoding="utf-8", errors="replace")
    if SOLAR_NOTES_CUTOFF not in body:
        return {"ok": False, "detail": "automation_cutoff_missing", "path": str(target)}
    normalized = event.strip().upper()
    if normalized not in {"STARTED", "STOPPED"}:
        return {"ok": False, "detail": "invalid_lifecycle_event", "event": event}
    line = _automated_lines(now.strftime("%H%M"), f"AVA {normalized}")
    updated, inserted = _append_report_inserts(body, [line])
    if inserted:
        target.write_text(updated, encoding="utf-8", newline="\n")
    return {"ok": True, "path": str(target), "detail": "inserted" if inserted else "already_present", "line": line}


def append_ecoflow_automation_event(event: str, now: datetime | None = None) -> dict:
    """Append one verified EcoFlow automation transition to the live report."""
    from zoneinfo import ZoneInfo

    allowed = {"GENERATOR STARTED", "GENERATOR STOPPED", "TRANSFER STARTED", "TRANSFER STOPPED"}
    normalized = " ".join(str(event or "").upper().split())
    if normalized not in allowed:
        return {"ok": False, "detail": "invalid_ecoflow_event", "event": event}
    now = now or datetime.now(ZoneInfo("Pacific/Honolulu"))
    target = hybrid_daily_report_path(now)
    if not target.is_file():
        return {"ok": False, "detail": "hybrid_report_missing", "path": str(target)}
    body = target.read_text(encoding="utf-8", errors="replace")
    if SOLAR_NOTES_CUTOFF not in body:
        return {"ok": False, "detail": "automation_cutoff_missing", "path": str(target)}
    line = _automated_lines(now.strftime("%H%M"), f"ECOFLOW AUTOMATION — {normalized}")
    updated, inserted = _append_report_inserts(body, [line])
    if inserted:
        target.write_text(updated, encoding="utf-8", newline="\n")
    return {"ok": True, "path": str(target), "detail": "inserted" if inserted else "already_present", "line": line}


def _media_current(name: str) -> Path | None:
    path = config.REPORTS_DIR / name
    try:
        if path.is_file() and path.stat().st_size > 0:
            return path
    except OSError:
        return None
    return None


def _first_prose(path: Path, *, skip_headings: bool = True) -> str:
    try:
        raw = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""
    bits: list[str] = []
    for line in raw.splitlines():
        s = line.strip()
        if not s:
            continue
        if skip_headings and (s.startswith("#") or s.startswith("---")):
            continue
        bits.append(re.sub(r"\s+", " ", s))
        if sum(len(b) for b in bits) > 400:
            break
    return " ".join(bits).strip()


def _weather_headline() -> str:
    current = _media_current("nws-hawaii-counties-current.md")
    if current:
        text = current.read_text(encoding="utf-8", errors="replace")
        spoken = re.search(r"## Spoken\s*\n+(.+)", text)
        if spoken:
            return re.sub(r"\s+", " ", spoken.group(1)).strip()
        prose = _first_prose(current)
        if prose:
            return prose
    try:
        from apps.core.services.reports import latest_report

        weather_report = latest_report("nws-weather-*.md")
        if weather_report:
            match = re.search(
                r"^###?\s*[^\n]+\n([^\n]+)",
                weather_report.read_text(encoding="utf-8", errors="replace"),
                re.M,
            )
            if match:
                return re.sub(r"\s+", " ", match.group(1)).strip()
    except Exception:
        pass
    return "n/a"


def _kilauea_headline() -> str:
    current = _media_current("kilauea-current.md")
    if current:
        prose = _first_prose(current, skip_headings=False)
        if prose:
            return prose[:900]
    try:
        from apps.core.services.reports import latest_report

        kilauea_report = latest_report("kilauea-*.md")
        if kilauea_report:
            return re.sub(
                r"\s+",
                " ",
                kilauea_report.read_text(encoding="utf-8", errors="replace"),
            ).strip()[:900]
    except Exception:
        pass
    return ""


def _hybrid_prediction_inserts(stamp: str) -> tuple[str | None, str | None]:
    weather_insert = None
    kilauea_insert = None
    try:
        from apps.core.services.reports import latest_report

        weather_report = _media_current("nws-hawaii-counties-current.md") or latest_report("nws-weather-*.md")
        if weather_report:
            weather_body = weather_report.read_text(encoding="utf-8", errors="replace")
            periods = []
            for match in re.finditer(r"^###\s+([^\n]+)\n(.*?)(?=^###\s+|\Z)", weather_body, re.M | re.S):
                period_lines = [re.sub(r"\s+", " ", line).strip() for line in match.group(2).splitlines() if line.strip()]
                if len(period_lines) >= 2:
                    periods.append((match.group(1), period_lines[0], period_lines[1]))
            windows = [(f"{name.strip()}: {summary}", detail) for name, summary, detail in periods[:4]]
            alerts = re.findall(r"\*\*([^*]+)\*\*[^\n]*\n([^\n]*\buntil\b[^\n]+)", weather_body, re.I)
            windows.extend(
                (f"ALERT — {name.strip()}", re.sub(r"\s+", " ", window).strip())
                for name, window in alerts[:8]
            )
            spoken = _weather_headline()
            if spoken and spoken != "n/a":
                windows = [("Spoken", spoken), *windows]
            if windows:
                weather_insert = _automated_section(stamp, "WEATHER WINDOWS", windows)

        kilauea_body = _kilauea_headline()
        if kilauea_body:
            kilauea_insert = _automated_section(
                stamp,
                "KILAUEA PREDICTION",
                [("Status", kilauea_body[:900])],
            )
    except Exception:
        pass
    return weather_insert, kilauea_insert


def _replace_hybrid_prediction_sections(body: str, weather_insert: str | None, kilauea_insert: str | None) -> tuple[str, bool]:
    updated = body
    changed = False
    if weather_insert:
        pattern = re.compile(r"(?ms)^(### Weather Forecast\s*\n).*?(?:^\+\+\+END WEATHER FORECAST\s*$|(?=^### Kilauea Prediction\s*$))")
        updated, count = pattern.subn(f"\\1{weather_insert}\n+++END WEATHER FORECAST\n\n", updated, count=1)
        changed = changed or bool(count)
    if kilauea_insert:
        pattern = re.compile(r"(?ms)^(### Kilauea Prediction\s*\n).*?(?:^\+\+\+END KILAUEA PREDICTION\s*$|(?=^## HYBRID NOTES FOR ))")
        updated, count = pattern.subn(f"\\1{kilauea_insert}\n+++END KILAUEA PREDICTION\n\n", updated, count=1)
        changed = changed or bool(count)
    return updated, changed


def _remove_legacy_prediction_inserts(body: str) -> str:
    marker = re.search(r"^## HYBRID NOTES FOR .*?$", body, re.M)
    if not marker:
        return body
    prefix, notes = body[:marker.end()], body[marker.end():]
    legacy = re.compile(
        r"(?m)^(?:>\d{4}, (?:WEATHER WINDOWS|KILAUEA PREDICTION) —|"
        r"> \[KILAUEA_PREDICTION - INSERT ON AVA BOOT\]).*"
        r"(?:\n(?!>\d{4}, )>[^\n]*)*\n?"
    )
    return prefix + legacy.sub("", notes)


def _mirror_hybrid_lines_to_skills(body: str, now: datetime, path: Path | None = None) -> None:
    """Slice today's hybrid ◇ lines into skill DAILY.md — same format, no extra store."""
    candidates = (
        Path(config.AVA_HOME) / ".cursor" / "skills",
        Path.home() / ".ollama" / "skills",
        Path.home() / "RootRecord" / "Ava-Core" / ".cursor" / "skills",
    )
    skills = next((p for p in candidates if p.is_dir()), None)
    if skills is None:
        return
    source = path or hybrid_daily_report_path(now)
    diamonds = [ln.rstrip() for ln in body.splitlines() if ln.startswith("> ◇ **")]
    weather = [ln for ln in diamonds if "WEATHER" in ln or "KILAUEA" in ln]
    eco = [
        ln
        for ln in diamonds
        if "ECOFLOW" in ln or "POWER AUTOMATION" in ln or "GENERATOR" in ln
    ]
    charge = [ln for ln in diamonds if "CHARGE STATUS" in ln or "AVA STARTED" in ln or "AVA STOPPED" in ln]
    reports = [ln for ln in diamonds if "WEATHER" in ln or "CHARGE STATUS" in ln]

    def write_slice(rel: str, title: str, lines: list[str]) -> None:
        dest_dir = skills / rel
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / "DAILY.md"
        header = (
            f"# {title}\n\n"
            f"Hybrid daily notebook: `{source}`\n\n"
            "Same `> ◇ **HHMM** —` inserts as the hybrid report. "
            "Numbers only from those inserts.\n\n"
        )
        dest.write_text(header + ("\n\n".join(lines[-48:]) + "\n" if lines else "_No hybrid lines yet today._\n"), encoding="utf-8")

    write_slice("weather-kilauea", "Weather / Kīlauea — hybrid lines", weather)
    write_slice("ecoflow-automations", "EcoFlow — hybrid lines", eco)
    write_slice("media-hybrid", "Hybrid notebook — all stamps", diamonds[-48:])
    write_slice("reports-voice", "Reports / charge — hybrid lines", reports)
    write_slice("boot-idle-origin", "Boot / host — hybrid lines", charge)
    write_slice("scheduler-clock", "Scheduler-adjacent hybrid stamps", diamonds[-24:])
    skip = {
        "weather-kilauea",
        "ecoflow-automations",
        "media-hybrid",
        "reports-voice",
        "boot-idle-origin",
        "scheduler-clock",
    }
    for dest_dir in skills.iterdir():
        if not dest_dir.is_dir() or dest_dir.name in skip:
            continue
        if not (dest_dir / "SKILL.md").is_file():
            continue
        write_slice(dest_dir.name, f"{dest_dir.name} — hybrid notebook pointer", diamonds[-12:])


def update_solar_notes(now: datetime | None = None, path: Path | None = None) -> dict:
    from zoneinfo import ZoneInfo

    target = path or hybrid_daily_report_path(now or datetime.now(ZoneInfo("Pacific/Honolulu")))
    if not target.is_file():
        return {"ok": False, "detail": "solar_notes_missing", "path": str(target)}
    body = target.read_text(encoding="utf-8", errors="replace")
    if SOLAR_NOTES_CUTOFF not in body:
        return {"ok": False, "detail": "automation_cutoff_missing", "path": str(target)}

    now = now or datetime.now(ZoneInfo("Pacific/Honolulu"))
    now_ms = int(now.timestamp() * 1000)
    start_ms = now_ms - 15 * 60 * 1000
    latest: dict[str, dict] = {}
    samples: dict[str, list[dict[str, float | int | None]]] = {"delta": [], "river": []}
    for root in _ecoflow_roots():
        hist = root / "history"
        if not hist.is_dir():
            continue
        for device, row in _iter_history_rows(hist):
            role = _device_role(device)
            if role not in samples:
                continue
            at_ms = _parse_history_at_ms(row, now_ms)
            if at_ms is None or at_ms > now_ms or at_ms < start_ms:
                continue
            src = str(row.get("source") or "").lower()
            if src and src not in {"ble", "cloud"}:
                continue
            try:
                sample = {"solar_w": float(row.get("solarW")) if row.get("solarW") is not None else None, "in_w": float(row.get("inW")) if row.get("inW") is not None else None, "out_w": float(row.get("outW")) if row.get("outW") is not None else None, "soc": float(row.get("soc")) if row.get("soc") is not None else None, "at_ms": at_ms}
            except (TypeError, ValueError):
                continue
            # A shared Delta AC-output ghost can show up as River "inW" with zero
            # River load and zero River PV. That is not pack charging and must not
            # be counted as River intake.
            if (
                role == "river"
                and sample.get("in_w") is not None
                and sample.get("in_w") > 0
                and sample.get("solar_w") in (None, 0)
                and sample.get("out_w") in (None, 0)
            ):
                sample["in_w"] = 0.0
            samples[role].append(sample)
            if role not in latest or at_ms > latest[role]["at_ms"]:
                latest[role] = sample

    def avg(role: str, key: str) -> str:
        values = [float(sample[key]) for sample in samples[role] if sample.get(key) is not None]
        return f"{sum(values) / len(values):.0f} W" if values else "Waiting"

    def pct(role: str) -> str:
        value = latest.get(role, {}).get("soc")
        return f"{float(value):.0f}%" if value is not None and 0 <= float(value) <= 100 else "Waiting"

    weather_line = _weather_headline()

    stamp = now.strftime("%H%M")
    weather_insert = _automated_lines(stamp, f"WEATHER — {weather_line}")
    ecoflow_inserts = [
        _automated_lines(
            stamp,
            f"ECOFLOW STATUS - DELTA 2: Average 15-minute In/Out "
            f"{avg('delta', 'in_w')} / {avg('delta', 'out_w')} | Current {pct('delta')}",
        ),
        _automated_lines(
            stamp,
            f"ECOFLOW STATUS - RIVER 2 PRO: Average 15-minute In/Out "
            f"{avg('river', 'in_w')} / {avg('river', 'out_w')} | Current {pct('river')}",
        ),
    ]
    weather_windows_insert, kilauea_insert = _hybrid_prediction_inserts(stamp)
    inserts = [
        item
        for item in (
            _charge_status_insert(now, body),
            _power_automation_insert(now),
            _generator_insert(now),
            weather_insert,
            *ecoflow_inserts,
        )
        if item
    ]
    cleaned_body = _remove_legacy_prediction_inserts(_strip_legacy_automation_lines(body))
    updated, sections_changed = _replace_hybrid_prediction_sections(cleaned_body, weather_windows_insert, kilauea_insert)
    updated, inserted = _append_report_inserts(updated, inserts)
    changed = inserted or sections_changed or updated != body
    if changed:
        target.write_text(updated, encoding="utf-8", newline="\n")
    try:
        _mirror_hybrid_lines_to_skills(updated if changed else body, now, path=target)
    except Exception:
        pass
    return {"ok": True, "path": str(target), "stamp": stamp, "delta_samples": len(samples["delta"]), "river_samples": len(samples["river"]), "detail": "updated" if changed else "already_present"}


def update_hybrid_daily_report(now: datetime | None = None) -> dict:
    from zoneinfo import ZoneInfo

    now = now or datetime.now(ZoneInfo("Pacific/Honolulu"))
    return update_solar_notes(now, path=ensure_hybrid_daily_report(now))


def update_hybrid_charge_status(now: datetime | None = None) -> dict:
    from zoneinfo import ZoneInfo

    now = now or datetime.now(ZoneInfo("Pacific/Honolulu"))
    target = hybrid_daily_report_path(now)
    if not target.is_file():
        return {"ok": False, "detail": "hybrid_report_missing", "path": str(target)}
    body = target.read_text(encoding="utf-8", errors="replace")
    if SOLAR_NOTES_CUTOFF not in body:
        return {"ok": False, "detail": "automation_cutoff_missing", "path": str(target)}
    charge_insert = _charge_status_insert(now, body)
    if not charge_insert:
        return {"ok": True, "path": str(target), "detail": "unchanged"}
    updated, inserted = _append_report_inserts(body, [charge_insert])
    if inserted:
        target.write_text(updated, encoding="utf-8", newline="\n")
    return {"ok": True, "path": str(target), "detail": "inserted" if inserted else "already_present"}


__all__ = [
    "HYBRID_REPORT_ROOT",
    "append_hybrid_lifecycle_event",
    "append_ecoflow_automation_event",
    "ensure_hybrid_daily_report",
    "hybrid_daily_report_path",
    "update_hybrid_charge_status",
    "update_hybrid_daily_report",
    "update_solar_notes",
]
