"""Read-only tree access for council. Always on for allowlisted paths. Never dumps secrets."""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from .config import RUNS_DIR

REPO = Path("/home/rootrecord/.ollama/skills/origin")
OLLAMA_SKILLS = Path.home() / ".ollama" / "skills"
CAP = 12_000
ALLOW_PREFIXES = (
    "apps/",
    "scripts/",
    "tests/",
    "docs/",
    "data/logs/",
    "data/state/",
    ".cursor/skills/",
)
DENY_NAMES = {
    ".env",
    ".env.local",
    "secrets.env",
    "credentials.json",
    "credentials",
}
DENY_PARTS = {".git", ".venv", "node_modules", "credentials", "Media", "Media.bak-20260915"}
SECRET_MARK = ("api_key", "bot_token", "telegram_ava_token", "begin private")


def _denied(path: Path) -> bool:
    name = path.name.lower()
    if name in {n.lower() for n in DENY_NAMES}:
        return True
    if name.endswith(".pem") or name.endswith(".key"):
        return True
    parts = {p.lower() for p in path.parts}
    return bool(parts & {p.lower() for p in DENY_PARTS})


def resolve(rel: str) -> Path | None:
    raw = (rel or "").strip()
    while raw.startswith("./"):
        raw = raw[2:]
    if not raw or raw.startswith("/") or ".." in Path(raw).parts:
        return None
    posix = raw.replace("\\", "/")
    if posix in {"data/state/ecoflow-live.json", "ecoflow-live.json"}:
        from apps.core.services.ecoflow_public import live_path

        return live_path()
    if not posix.startswith(ALLOW_PREFIXES):
        return None
    path = (REPO / posix).resolve()
    allowed_roots = [REPO.resolve()]
    if OLLAMA_SKILLS.is_dir():
        allowed_roots.append(OLLAMA_SKILLS.resolve())
    ok = False
    for root in allowed_roots:
        try:
            path.relative_to(root)
            ok = True
            break
        except ValueError:
            continue
    if not ok:
        return None
    if _denied(path):
        return None
    return path


def _state_file(name: str) -> Path | None:
    """Prefer origin STATE_DIR, then the state skill store."""
    from apps.core import config as core_config

    for root in (
        core_config.STATE_DIR,
        Path.home() / ".ollama" / "skills" / "state" / "store",
    ):
        path = root / name
        if path.is_file():
            return path
    return None


def _load_json(name: str) -> dict[str, Any] | None:
    path = _state_file(name)
    if path is None:
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8", errors="replace"))
    except (OSError, json.JSONDecodeError):
        return None
    return data if isinstance(data, dict) else None


def _situation_summary(body: str, *, cap: int = 420) -> str:
    text = re.sub(r"(?i)&nbsp;|&amp;|&lt;|&gt;", " ", body or "")
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    m = re.search(
        r"Summary:\s*(.+?)(?:Eruption History:|Description of Current|$)",
        text,
        re.I,
    )
    if m:
        text = m.group(1).strip()
    return text[:cap]


def kilauea_prompt_block(*, cap: int = 700) -> str:
    """HVO/USGS facts from disk. Never a Wikipedia year."""
    alert = _load_json("kilauea-alert.json") or {}
    dash = _load_json("kilauea-dashboard.json") or {}
    sit = _load_json("kilauea-situation.json") or {}
    quakes = _load_json("kilauea-quakes.json") or {}
    if not alert and not sit and not dash:
        return (
            "Kīlauea desk: No data. Do not invent alert level, eruption date, "
            "or quake counts. Say No data."
        )
    nested = dash.get("kilauea") if isinstance(dash.get("kilauea"), dict) else {}
    level = str(alert.get("alert_level") or nested.get("alert_level") or "unknown")
    if "erupting" in alert:
        erupting = bool(alert.get("erupting"))
    else:
        erupting = bool(nested.get("erupting"))
    head = str(alert.get("headline") or nested.get("headline") or "").strip()
    sit_row = sit.get("situation") if isinstance(sit.get("situation"), dict) else {}
    updated = str(
        alert.get("updated_at")
        or dash.get("updated_at")
        or sit_row.get("updated_at")
        or ""
    ).strip()
    state = "erupting" if erupting else "not erupting"
    lines = [
        "Kīlauea from desk files (use only this; never invent last-erupted years):",
        f"alert={level} {state}" + (f" — {head}" if head else ""),
    ]
    if updated:
        lines.append(f"sample {updated}")
    summary = _situation_summary(str(sit_row.get("body") or ""))
    if summary:
        lines.append("HVO summary: " + summary)
    qn = dash.get("quakes_n")
    if qn is not None:
        lines.append(f"USGS events on file: {qn}")
    rows = quakes.get("quakes") if isinstance(quakes.get("quakes"), list) else []
    bits = []
    for row in rows[:3]:
        if not isinstance(row, dict):
            continue
        mag = row.get("mag")
        place = str(row.get("place") or "").strip()
        if mag is None:
            continue
        bits.append(f"M{mag} {place}".strip())
    if bits:
        lines.append("Recent: " + "; ".join(bits))
    lines.append(
        "If a field is missing, say No data. Do not fill from memory or Wikipedia."
    )
    blob = "\n".join(lines)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def _nws_line() -> str:
    data = _load_json("nws-hawaii.json") or {}
    spoken = str(data.get("spoken") or "").strip()
    if spoken:
        return "NWS Hawaii: " + spoken[:280]
    if data.get("ok"):
        n = data.get("alert_count")
        return f"NWS Hawaii: alert_count={n}" if n is not None else "NWS Hawaii: No data"
    return "NWS Hawaii: No data"


def _hurricane_desk_line() -> str:
    data = _load_json("hurricane-desk.json") or {}
    hi = data.get("hawaii") if isinstance(data.get("hawaii"), dict) else {}
    nm = hi.get("nm")
    try:
        nmi = float(nm) if nm is not None else None
    except (TypeError, ValueError):
        nmi = None
    if nmi is not None and nmi >= 800:
        name = hi.get("label") or hi.get("name") or "storm"
        island = hi.get("island") or "Hawaiʻi"
        bear = hi.get("bearing") or ""
        return (
            f"Hurricane desk: NOT a Hawaiʻi threat. {name} is {int(nmi)} nmi "
            f"{bear} of {island}."
            f"{' ' + str(hi.get('region_name')) + '.' if hi.get('region_name') else ''}"
            f"{' Motion ' + str(hi.get('movement_compass') or '') + ' ' + str(hi.get('hawaii_approach') or '') + '.' if hi.get('movement_compass') or hi.get('hawaii_approach') else ''}"
            " West of Kauaʻi is Asia/Japan. Do not alarm."
        )
    spoken = str(hi.get("spoken") or "").strip()
    if spoken:
        return "Hurricane desk: " + spoken[:280]
    storms = _load_json("hurricanes.json") or {}
    n = storms.get("count")
    if n is None:
        return "Hurricanes: No data"
    return f"Hurricanes on file: {n} (no Hawaii spoken line)"


def _sun_line() -> str:
    data = _load_json("sun-times.json") or {}
    day = str(data.get("date") or "").strip()
    up = str(data.get("sunrise") or "").strip()
    down = str(data.get("sunset") or "").strip()
    if not (day and up and down):
        return "Sun times: No data"
    return f"Sun {day} HST sunrise {up} sunset {down}"


def _minecraft_line() -> str:
    data = _load_json("minecraft-live.json") or {}
    if not data:
        return "RootMC client: No data"
    ingame = bool(data.get("ingame") or data.get("client_now"))
    return "RootMC client: in-game" if ingame else "RootMC client: not in-game"


def _due_line() -> str:
    data = _load_json("remaining-tasks.json") or {}
    items = data.get("auto") if isinstance(data.get("auto"), list) else []
    names = []
    for row in items[:2]:
        if isinstance(row, dict) and row.get("label"):
            names.append(str(row.get("label")).strip())
    if not names:
        return "Due soon: none on file"
    return "Due soon: " + "; ".join(names)


def _short(text: str, n: int) -> str:
    s = re.sub(r"\s+", " ", (text or "").strip())
    if len(s) <= n:
        return s
    return s[: n - 1] + "…"


def _weather_section(*, row_cap: int = 220) -> list[str]:
    try:
        from apps.core.services.live_wx import weather_lines_sync

        return [_short(row, row_cap) for row in weather_lines_sync()]
    except Exception:
        return ["Weather: No data", "HI alerts: No data", "Hurricanes: No data"]


def _ask_wants_weather(ask: str) -> bool:
    t = (ask or "").lower().replace("ī", "i").replace("ʻ", "").replace("'", "")
    return any(
        k in t
        for k in (
            "weather",
            "forecast",
            "nws",
            "rain",
            "storm",
            "thunder",
            "flood",
            "wind",
            "tomorrow",
            "tonight",
            "humidity",
            "temperature",
            "how wet",
            "how's the weather",
            "how is the weather",
        )
    )


def desk_facts_block(*, cap: int = 2200, ask: str = "") -> str:
    """Measured desk sources. Missing = No data. Never invent. No player counts.

    When the human ask is about weather, put weather/NWS first so prompt budgets
    cannot truncate them behind Kīlauea.
    """
    wx = _weather_section()
    wx.append(_short(_nws_line(), 240))
    kilauea = kilauea_prompt_block(cap=min(380, max(160, cap // 5)))
    storm_bits: list[str] = [_short(_hurricane_desk_line(), 240)]
    try:
        from pathlib import Path
        import sys as _sys

        _sp = Path.home() / ".ollama" / "skills" / "hurricane-desk" / "scripts"
        if str(_sp) not in _sys.path:
            _sys.path.insert(0, str(_sp))
        from storm_plot import prompt_line as _storm_plot_line

        storm_bits.append(_short(_storm_plot_line(), 280))
    except Exception:
        storm_bits.append("Storm plot: No data")
    host_bits: list[str] = []
    try:
        from apps.core.services import db_facts

        host_bits.append(_short(db_facts.ecoflow_line(), 280))
        host_bits.append(_short(db_facts.host_line(), 180))
    except Exception:
        host_bits.extend(["EcoFlow: No data", "Host: No data"])
    host_bits.extend([_sun_line(), _minecraft_line(), _due_line()])

    if _ask_wants_weather(ask):
        body = wx + [kilauea] + storm_bits + host_bits
        rule = (
            "Desk live files (quote these; never say you lack live weather when Weather/Next/NWS lines are here):"
        )
    else:
        body = [kilauea] + wx + storm_bits + host_bits
        rule = "Desk live files (quote these or say No data — never invent; never claim no live data when a line is present):"

    lines = [rule, *body]
    blob = "\n".join(ln for ln in lines if ln)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def brainstorm_desk_block(topic: str, *, cap: int = 400) -> str:
    """Only the live desks the topic actually names. Empty topic → no dump."""
    t = (topic or "").lower().replace("ī", "i").replace("ʻ", "").replace("'", "")
    note = (
        "Brainstorm: quote a live desk only if the topic names it. "
        "Otherwise PASS rather than weather, volcano, EcoFlow, or leftover plans."
    )
    if not t.strip():
        return note
    chunks: list[str] = []
    if any(k in t for k in ("kilauea", "volcano", "hvo", "erupt")):
        chunks.append(kilauea_prompt_block(cap=min(380, cap)))
    if any(k in t for k in ("weather", "nws", "rain", "wind", "forecast", "thunder", "tomorrow", "tonight")):
        chunks.extend(_weather_section())
        chunks.append(_short(_nws_line(), 240))
    if any(k in t for k in ("hurricane", "storm", "cyclone", "typhoon")):
        chunks.append(_short(_hurricane_desk_line(), 240))
    if any(k in t for k in ("ecoflow", "generator", "delta", "watts", "solar")):
        try:
            from apps.core.services import db_facts

            chunks.append(_short(db_facts.ecoflow_line(), 280))
        except Exception:
            chunks.append("EcoFlow: No data")
    if any(k in t for k in ("cpu", "ram", "host", "npu", "ollama", "fastflow", "xmrig")):
        try:
            from apps.core.services import db_facts

            chunks.append(_short(db_facts.host_line(), 180))
        except Exception:
            chunks.append("Host: No data")
    if any(k in t for k in ("minecraft", "rootmc")):
        chunks.append(_minecraft_line())
    if not chunks:
        return note
    blob = note + "\n" + "\n".join(chunks)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def _clip(text: str, cap: int) -> str:
    low = text.lower()
    if any(m in low for m in SECRET_MARK) and any(
        line.strip().lower().startswith(("token=", "api_key=", "password="))
        for line in text.splitlines()[:40]
    ):
        return "blocked (looked like a secret file)"
    if len(text) > cap:
        return text[: cap - 1] + "…"
    return text


def read_proposal(pid: str, *, cap: int = CAP) -> str:
    from . import proposals

    name = proposals.normalize_id(pid)
    if not name.startswith("plan-"):
        return "blocked path"
    path = proposals.proposal_path(name)
    if not path.is_file():
        return "missing file"
    try:
        path.resolve().relative_to(RUNS_DIR.resolve())
    except ValueError:
        return "blocked path"
    try:
        return _clip(path.read_text(encoding="utf-8", errors="replace"), cap)
    except OSError as e:
        return f"read failed ({type(e).__name__})"


def read_status(name: str, *, cap: int = CAP) -> str:
    from . import boot_brief

    raw = Path(name or "").name
    if raw not in boot_brief.CLEAN_CURRENT and not raw.endswith("-boot-current.md"):
        return "blocked path"
    path = boot_brief.reports_dir() / raw
    if not path.is_file():
        return "missing file"
    try:
        return _clip(path.read_text(encoding="utf-8", errors="replace"), cap)
    except OSError as e:
        return f"read failed ({type(e).__name__})"


def read_spec(spec: str, *, cap: int = CAP) -> str:
    raw = (spec or "").strip()
    if not raw:
        return "empty path"
    low = raw.lower()
    if low in {"ecoflow", "generator", "ecoflow-live", "status:ecoflow"}:
        from apps.core.services.ecoflow_public import live_path

        path = live_path()
        if not path.is_file():
            return "No data"
        try:
            return _clip(path.read_text(encoding="utf-8", errors="replace"), cap)
        except OSError as e:
            return f"read failed ({type(e).__name__})"
    if low in {"kilauea", "kilauea-alert", "status:kilauea"}:
        return kilauea_prompt_block(cap=cap)
    if low in {"desk", "desk-facts", "live", "status:desk"}:
        return desk_facts_block(cap=cap)
    if low in {"weather", "nws", "status:weather"}:
        return _nws_line()
    if low in {"hurricane", "hurricanes", "status:hurricane"}:
        return _hurricane_desk_line()
    if low in {"host", "status:host"}:
        try:
            from apps.core.services import db_facts

            return db_facts.host_line()
        except Exception:
            return "Host: No data"
    if low in {"sun", "sun-times", "status:sun"}:
        return _sun_line()
    if low in {"minecraft", "rootmc", "status:minecraft"}:
        return _minecraft_line()
    if low.startswith("skill:") or low.startswith("desk:"):
        name = raw.split(":", 1)[-1].strip().strip("/")
        if not name or "/" in name or ".." in name:
            return "blocked path"
        daily = REPO / ".cursor" / "skills" / name / "DAILY.md"
        index = REPO / ".cursor" / "skills" / name / "INDEX.md"
        if daily.is_file():
            return read_rel(f".cursor/skills/{name}/DAILY.md", cap=cap)
        if index.is_file():
            return read_rel(f".cursor/skills/{name}/INDEX.md", cap=cap)
        return "missing file"
    if low.startswith("plan-") or low.startswith("proposal:"):
        pid = raw.split(":", 1)[-1]
        return read_proposal(pid, cap=cap)
    if low.startswith("status:") or low.startswith("report:"):
        return read_status(raw.split(":", 1)[-1], cap=cap)
    if Path(raw).name in {
        "morning-boot-current.md",
        "midday-boot-current.md",
        "morning-report-current.md",
        "evening-current.md",
    }:
        return read_status(Path(raw).name, cap=cap)
    return read_rel(raw, cap=cap)


def snapshot_for_prompt(*, cap: int = 1400) -> str:
    from . import boot_brief, proposals

    lines = [
        "Desk files are readable here. Discord/Slack live hooks are not wired; local files only.",
        "Ops desks: `~/.ollama/skills/<topic>/` — INDEX.md, DAILY.md, desk/src. `/read skill:weather-kilauea` (or ecoflow-automations, media-hybrid, council-telegram, …).",
        desk_facts_block(cap=min(1500, max(400, cap - 200))),
    ]
    items = [
        it
        for it in proposals.load_index().get("items", {}).values()
        if isinstance(it, dict)
    ]
    items.sort(key=lambda r: int(r.get("created") or 0), reverse=True)
    if items:
        names = [str(it.get("id") or "") for it in items[:6] if it.get("id")]
        lines.append("Proposal files (frozen after filing): " + ", ".join(names))
        latest = str(items[0].get("id") or "")
        if latest:
            excerpt = read_proposal(latest, cap=500)
            if excerpt and not excerpt.startswith("missing") and not excerpt.startswith("blocked"):
                lines.append(f"Latest `{latest}` excerpt:\n{excerpt}")
    reports = boot_brief.select_clean_reports()
    if reports:
        lines.append("Today status files: " + ", ".join(p.name for p in reports))
    else:
        lines.append("Today status files: none on disk for this slot.")
    blob = "\n".join(lines)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def read_rel(rel: str, *, cap: int = CAP) -> str:
    path = resolve(rel)
    if path is None:
        return "blocked path"
    if not path.is_file():
        return "missing file"
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError as e:
        return f"read failed ({type(e).__name__})"
    return _clip(text, cap)
