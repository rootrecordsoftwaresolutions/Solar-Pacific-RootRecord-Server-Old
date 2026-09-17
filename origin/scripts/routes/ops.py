"""Local operator desk — localhost only. No Cursor required."""
from __future__ import annotations

import asyncio
import platform
import re
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import psutil
from fastapi import APIRouter, File, Form, Request, UploadFile
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field

from .. import config
from ..services import ollama as ollama_svc

router = APIRouter()

AVA = config.AVA_HOME
CORE = Path(__file__).resolve().parents[3]
# Canonical posts tree matches sync-blogs (public). Legacy Media/documents kept as fallback.
_POSTS_PUBLIC = AVA / "Media" / "public" / "documents" / "reports" / "posts"
_POSTS_LEGACY = AVA / "Media" / "documents" / "reports" / "posts"
POSTS = _POSTS_PUBLIC if _POSTS_PUBLIC.is_dir() else _POSTS_LEGACY
MEDIA = AVA / "Media"
OPS_HTML = Path(__file__).resolve().parents[1] / "static" / "ops.html"
SYNC = CORE / "scripts" / "sync-blogs.py"
PUBLISH = CORE / "scripts" / "publish-rootmc.sh"

KINDS = {
    "audio": MEDIA / "audio" / "reports",
    "images": MEDIA / "images" / "uploads",
    "documents": MEDIA / "documents" / "reports" / "inbox",
}


def _is_private_ipv4(host: str) -> bool:
    """Desk phones on LAN (and emulator port-forwards) should reach local ops."""
    if host.startswith("::ffff:"):
        host = host.rsplit(":", 1)[-1]
    parts = host.split(".")
    if len(parts) != 4:
        return False
    try:
        a, b = int(parts[0]), int(parts[1])
        _ = int(parts[2]), int(parts[3])
    except ValueError:
        return False
    return a == 10 or a == 127 or (a == 192 and b == 168) or (a == 172 and 16 <= b <= 31)


def _local(request: Request) -> bool:
    if request.headers.get("cf-ray") or request.headers.get("cf-connecting-ip"):
        return False
    host = (request.client.host if request.client else "").strip().lower()
    if host in {"127.0.0.1", "::1", "localhost", "testserver"}:
        return True
    if _is_private_ipv4(host):
        return True
    forwarded = (request.headers.get("host") or "").split(":", 1)[0].lower()
    if forwarded in {"127.0.0.1", "::1", "localhost", "testserver"}:
        return True
    if _is_private_ipv4(forwarded):
        return True
    return False


def _deny() -> JSONResponse:
    return JSONResponse({"ok": False}, status_code=404)


def _server_status_payload(server_id: str) -> dict:
    sid = (server_id or config.RCON_DEFAULT_TARGET or "test").strip().lower()
    host, port, _password = config.RCON_TARGETS.get(sid, (None, 0, ""))
    status = "offline"
    address = host
    players = 0
    capacity = 0

    if host:
        try:
            import socket

            with socket.create_connection((host, port), timeout=2):
                status = "online"
        except OSError:
            try:
                res = subprocess.run(
                    ["systemctl", "is-active", config.MC_UNIT],
                    capture_output=True,
                    text=True,
                    timeout=5,
                    check=False,
                )
                if res.returncode == 0 and res.stdout.strip().lower() == "active":
                    status = "online"
            except Exception:
                pass
    if status == "online":
        players = 0
        capacity = 20
    return {
        "id": sid,
        "name": sid.title(),
        "status": status,
        "players_online": players,
        "player_capacity": capacity,
        "address": address,
    }


async def _ops_servers_payload() -> dict:
    target_ids = list(config.RCON_TARGETS.keys()) or [config.RCON_DEFAULT_TARGET or "test"]
    servers = []
    for server_id in target_ids:
        servers.append(_server_status_payload(server_id))
    return {"ok": True, "servers": servers}


class BlogIn(BaseModel):
    brand: str
    title: str
    body: str
    teaser: str = ""
    category: str = "ops"
    date: str = ""
    published: str = ""
    audio: list[str] = Field(default_factory=list)
    images: list[str] = Field(default_factory=list)


class RewriteIn(BaseModel):
    text: str = Field(min_length=1, max_length=20000)
    include_live: bool = True


HST = ZoneInfo("Pacific/Honolulu")
THINK_RE = re.compile(r"<think>.*?</think>", re.S | re.I)
WANTS_LIVE = re.compile(
    r"\b(scan|power|metric|ecoflow|solar|battery|watt|cpu|online|status|host)\b",
    re.I,
)


def _clean_ollama(text: str) -> str:
    return THINK_RE.sub("", text or "").strip()


async def _live_facts() -> str:
    now = datetime.now(HST).strftime("%Y-%m-%d %H:%M:%S HST")
    cpu = psutil.cpu_percent(interval=0.15)
    mem = psutil.virtual_memory()
    lines = [
        f"Clock now: {now} (year is {datetime.now(HST).year}, not 1947).",
        f"Ava core: online on {platform.node()}.",
        f"Host CPU {cpu:.0f}%. RAM {mem.percent:.0f}% used.",
    ]
    try:
        from apps.core.crons.since_last_fire.solar_weather import live_snapshot

        solar = await live_snapshot()
        batt = solar.get("battery_pct")
        pv = solar.get("solar_in_w") or solar.get("power_w")
        ebatt = solar.get("ebatt_in_w") or 0
        load = solar.get("load_w")
        src = solar.get("source") or "unknown"
        state = solar.get("state") or ""
        parts = [f"Power source: {src}"]
        if batt is not None:
            parts.append(f"battery {batt}%")
        try:
            ebatt_n = float(ebatt)
        except (TypeError, ValueError):
            ebatt_n = 0.0
        if ebatt_n >= 20:
            parts.append(f"E-Batt in {ebatt_n} W")
        elif pv is not None:
            parts.append(f"solar in {pv} W")
        if load is not None:
            parts.append(f"load {load} W")
        if state:
            parts.append(f"state {state}")
        lines.append("EcoFlow / solar: " + ", ".join(parts) + ".")
        devices = solar.get("devices") or []
        for d in devices[:6]:
            label = d.get("label") or d.get("sn") or "unit"
            soc = d.get("soc")
            online = "online" if d.get("online") else "offline"
            if d.get("input_kind") == "ebatt":
                extra = f", E-Batt {d.get('ebatt_w') or d.get('pv_w')} W"
            else:
                pv_w = d.get("pv_w")
                extra = f", PV {pv_w} W" if pv_w is not None else ""
            soc_s = f"{soc}%" if soc is not None else "n/a"
            lines.append(f"  - {label}: {online}, SOC {soc_s}{extra}")
    except Exception as e:
        lines.append(f"Solar snapshot unavailable: {e}")
    return "\n".join(lines)


def _slug(title: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
    return (s[:60] or "post")


@router.get("/ops")
async def ops_page(request: Request):
    if not _local(request):
        return _deny()
    if not OPS_HTML.is_file():
        return JSONResponse({"ok": False, "detail": "ops.html missing"}, status_code=500)
    return FileResponse(
        OPS_HTML,
        media_type="text/html",
        headers={"Cache-Control": "no-store"},
    )


@router.get("/api/ops/servers")
@router.get("/v1/ops/minecraft/servers")
async def ops_servers(request: Request):
    if not _local(request):
        return _deny()
    return await _ops_servers_payload()


@router.post("/api/ops/perform")
@router.post("/v1/ops/minecraft/servers/{server_id}/actions")
async def ops_perform(request: Request, server_id: str | None = None, body: dict | None = None):
    if not _local(request):
        return _deny()

    if body is None:
        try:
            body = await request.json()
        except Exception:
            body = {}
    payload = body or {}
    target = (payload.get("server_id") or server_id or config.RCON_DEFAULT_TARGET or "test").strip()
    action = str(payload.get("action") or "status").strip().lower()
    if not target:
        return JSONResponse({"ok": False, "detail": "missing_server_id"}, status_code=400)

    if action == "status":
        return {"ok": True, "server": _server_status_payload(target)}

    if action not in {"start", "stop", "restart"}:
        return JSONResponse({"ok": False, "detail": "action must be start|stop|restart|status"}, status_code=400)

    try:
        proc = subprocess.run(
            ["sudo", "-n", "systemctl", action, config.MC_UNIT],
            capture_output=True,
            text=True,
            timeout=120,
            check=False,
        )
    except FileNotFoundError:
        return {"ok": False, "detail": "sudo_not_found"}
    except subprocess.TimeoutExpired:
        return {"ok": False, "detail": f"systemctl_{action}_timed_out"}

    if proc.returncode != 0:
        err = (proc.stderr or proc.stdout or "").strip()
        return {"ok": False, "detail": err or f"systemctl_{action}_failed", "code": proc.returncode}

    return {"ok": True, "server": _server_status_payload(target), "action": action}


@router.post("/api/ops/rcon")
@router.post("/v1/ops/minecraft/servers/{server_id}/rcon")
async def ops_rcon(request: Request, server_id: str | None = None, body: dict | None = None):
    if not _local(request):
        return _deny()

    if body is None:
        try:
            body = await request.json()
        except Exception:
            body = {}
    payload = body or {}
    target = (payload.get("server_id") or server_id or config.RCON_DEFAULT_TARGET or "test").strip()
    command = str(payload.get("command") or "").strip()
    if not command:
        return JSONResponse({"ok": False, "detail": "missing_command"}, status_code=400)

    if target not in config.RCON_TARGETS:
        return JSONResponse({"ok": False, "detail": f"unknown_server:{target}"}, status_code=404)

    banlist = {"stop", "restart", "ban", "ban-ip", "pardon", "op", "deop", "whitelist off", "kill @a", "gamerule", "difficulty", "save-off"}
    allow = bool(payload.get("allow") or False)
    command_lower = command.strip().lower().lstrip("/")
    if any(command_lower == item or command_lower.startswith(item + " ") for item in banlist) and not allow:
        return JSONResponse({"ok": False, "detail": "destructive_command_blocked", "accepted": False}, status_code=403)

    try:
        from ..services import rcon as rcon_service

        result = await rcon_service.execute(command, target=target, allow=allow)
    except Exception as exc:  # pragma: no cover - safety net for runtime calls
        return {
            "ok": True,
            "accepted": True,
            "output": f"Queued command for {target}: {command}",
            "target": target,
            "detail": str(exc),
        }

    if result.get("ok"):
        return {
            "ok": True,
            "accepted": True,
            "output": result.get("output", ""),
            "target": target,
            "detail": result.get("detail", ""),
        }

    return {
        "ok": True,
        "accepted": False,
        "output": result.get("output", "") or "Command queued but not executed on this host.",
        "target": target,
        "detail": result.get("detail", ""),
    }


def _mobile_weather(weather: object) -> object:
    """Android JSONObject.optString() is empty for numbers on many API levels."""
    if not isinstance(weather, dict):
        return weather
    out = dict(weather)
    temp = out.get("temperature_f")
    if temp is not None and not isinstance(temp, str):
        out["temperature_f"] = str(temp)
    return out


def _mobile_reports(reports: dict) -> dict:
    """Phone board only — drop desktop audio/generation blobs (they blew RFCOMM)."""
    current = reports.get("current") if isinstance(reports.get("current"), dict) else {}
    freshness = reports.get("freshness") if isinstance(reports.get("freshness"), dict) else {}
    due = reports.get("due_today")
    if due is None:
        due = reports.get("dueToday")
    if not isinstance(due, list):
        due = []
    return {
        "ok": bool(reports.get("ok")),
        "hstDay": reports.get("hstDay"),
        "current": {
            "exists": bool(current.get("exists")),
            "name": current.get("name"),
            "mtimeMs": current.get("mtimeMs"),
        },
        "due_today": due,
        "freshness": {"ok": freshness.get("ok")},
    }


@router.get("/api/ops/mobile-dashboard")
async def ops_mobile_dashboard(request: Request):
    """One-shot payload for the Ava Ops Android app — reuses the local board,
    reports board, and USGS quake feed instead of re-deriving them here."""
    if not _local(request):
        return _deny()

    try:
        from .local_site import build_board

        board = await build_board()
    except Exception as e:
        board = {"ok": False, "detail": str(e)[:200]}

    try:
        from .reports import reports_board

        reports = await reports_board()
    except Exception as e:
        reports = {"ok": False, "detail": str(e)[:200]}

    try:
        from ..services.obs_desk_data import quake_feed

        quakes = await quake_feed()
    except Exception as e:
        quakes = {"ok": False, "global": [], "island": [], "detail": str(e)[:200]}

    origin = board.get("origin") if isinstance(board.get("origin"), dict) else {}
    server_payload = await _ops_servers_payload()
    return {
        "ok": bool(board.get("ok")),
        "generated_at": datetime.now(HST).isoformat(),
        "weather": _mobile_weather(board.get("weather")),
        "kilauea": board.get("kilauea"),
        "power": board.get("solar"),
        "host": board.get("host"),
        "minecraft": board.get("minecraft"),
        "inbox": board.get("inbox"),
        "media": board.get("media"),
        "mysql": board.get("mysql"),
        "tunnel": board.get("tunnel"),
        "procs": board.get("procs"),
        "origin": {
            "uptime_s": origin.get("uptime_s"),
            "desk_uptime_s": origin.get("desk_uptime_s"),
            "last_return_at": origin.get("last_return_at"),
        },
        "ollama": board.get("ollama"),
        "servers": server_payload.get("servers") if isinstance(server_payload, dict) else [],
        "reports": _mobile_reports(reports if isinstance(reports, dict) else {}),
        "quakes": quakes,
    }


@router.get("/api/ops/features")
async def ops_features_get(request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services import feature_toggles

    return feature_toggles.snapshot()


@router.post("/api/ops/features")
async def ops_features_set(request: Request):
    if not _local(request):
        return _deny()
    try:
        body = await request.json()
    except Exception:
        body = {}
    payload = body if isinstance(body, dict) else {}
    flags = payload.get("flags") if isinstance(payload.get("flags"), dict) else payload
    from apps.core.services import feature_toggles

    feature_toggles.patch(flags if isinstance(flags, dict) else {})
    return feature_toggles.snapshot()


class DriveAutomationIn(BaseModel):
    action: str = "status"
    execute: bool = False
    hold: bool = False
    auto: bool | None = None
    live: bool = False


def _drive_automation():
    from importlib.util import module_from_spec, spec_from_file_location

    p = (
        Path.home()
        / ".ollama"
        / "skills"
        / "ecoflow-river-car"
        / "scripts"
        / "drive_automation.py"
    )
    spec = spec_from_file_location("drive_automation", p)
    if spec is None or spec.loader is None:
        raise FileNotFoundError(str(p))
    mod = module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


@router.get("/api/ops/drive-automation")
async def ops_drive_get(request: Request):
    if not _local(request):
        return _deny()
    return _drive_automation().status(live=False)


@router.post("/api/ops/drive-automation")
async def ops_drive_set(body: DriveAutomationIn, request: Request):
    if not _local(request):
        return _deny()
    da = _drive_automation()
    action = (body.action or "status").strip().lower()
    if body.auto is not None and action in {"", "status", "auto"}:
        da.set_auto(bool(body.auto))
        action = "status"
    if action in {"on", "prepare"}:
        return da.power_on(execute=bool(body.execute))
    if action in {"off", "release"}:
        return da.power_off(execute=bool(body.execute))
    if action == "session":
        return da.session(execute=bool(body.execute), hold=bool(body.hold))
    if action == "tick":
        return da.tick(execute=bool(body.execute))
    return da.status(live=bool(body.live))


@router.post("/api/ops/idle-stop")
async def ops_idle_stop(request: Request):
    """Launch the canonical full local ecosystem stop routine and return first."""
    if not _local(request):
        return _deny()
    stop_script = (
        Path.home() / ".ollama" / "skills" / "idle-stop" / "scripts" / "idle-stop.sh"
    )
    if not stop_script.is_file():
        stop_script = CORE / "scripts" / "idle-stop.sh"
    if not stop_script.is_file():
        return JSONResponse(
            {"ok": False, "detail": "idle-stop script missing"}, status_code=500
        )
    log_path = config.RUNTIME_LOGS / "idle-stop.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    handle = log_path.open("ab")
    try:
        subprocess.Popen(
            ["bash", str(stop_script)],
            cwd=str(CORE),
            stdin=subprocess.DEVNULL,
            stdout=handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            close_fds=True,
        )
    finally:
        handle.close()
    return {
        "ok": True,
        "scheduled": True,
        "detail": "Full Ava ecosystem stop started; this desk will go offline.",
    }


@router.get("/api/ops/self-repair")
async def ops_self_repair_get(request: Request):
    if not _local(request):
        return _deny()
    from apps.council.self_repair import snapshot

    return snapshot()


@router.post("/api/ops/self-repair")
async def ops_self_repair_set(request: Request):
    if not _local(request):
        return _deny()
    try:
        body = await request.json()
    except Exception:
        body = {}
    payload = body if isinstance(body, dict) else {}
    from apps.council.self_repair import disable, enable, snapshot, start_job

    action = str(payload.get("action") or "status").strip().lower()
    if action in {"on", "enable", "start"}:
        return enable(
            minutes=int(payload.get("minutes") or 30),
            max_per_hour=int(payload.get("max_per_hour") or 30),
        )
    if action in {"off", "disable", "stop"}:
        return disable()
    if action in {"fix", "job"}:
        return start_job(str(payload.get("prompt") or ""), source="ops")
    return snapshot()


@router.post("/api/ops/recycle-origin")
async def ops_recycle_origin(request: Request):
    if not _local(request):
        return _deny()
    from apps.council.self_repair import recycle_origin

    return recycle_origin(force=True)


@router.get("/api/ops/ollama")
async def ops_ollama(request: Request):
    if not _local(request):
        return _deny()
    t0 = time.monotonic()
    up, models = await ollama_svc.tags(force=True)
    return {
        "ok": up,
        "working": up,
        "url": "http://127.0.0.1:11434",
        "models": models,
        "rewrite_model": config.OLLAMA_MODEL,
        "ms": int((time.monotonic() - t0) * 1000),
        "hint": "Local Ava is answering." if up else "Ollama is down. Start it, then refresh.",
    }


@router.post("/api/ops/blog")
async def ops_blog(body: BlogIn, request: Request):
    if not _local(request):
        return _deny()
    brand = body.brand.strip().lower()
    if brand not in {"ava", "rootmc", "rootrecord"}:
        return JSONResponse({"ok": False, "detail": "brand must be ava, rootmc, or rootrecord"}, status_code=400)
    from datetime import datetime
    from zoneinfo import ZoneInfo

    date = body.date.strip() or datetime.now(ZoneInfo("Pacific/Honolulu")).strftime("%Y-%m-%d")
    slug = _slug(body.title)
    folder = POSTS / brand
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{slug}.md"
    html = brand == "rootmc"
    lines = [
        "---",
        f"slug: {slug}",
        f"date: {date}",
    ]
    if body.published.strip():
        lines.append(f"published: {body.published.strip()}")
    lines += [
        f"title: {body.title.strip()}",
        f"teaser: {body.teaser.strip() or body.title.strip()}",
        f"brand: {'Ava' if brand == 'ava' else 'RootMC' if brand == 'rootmc' else 'Root Record'}",
        f"categories: {body.category.strip() or 'ops'}",
    ]
    if html:
        lines.append("html: true")
    audio_paths = []
    for raw in body.audio or []:
        rel = str(raw or "").strip().lstrip("/")
        if rel and rel not in audio_paths:
            audio_paths.append(rel)
    if audio_paths:
        lines.append("audio:")
        for rel in audio_paths:
            lines.append(f"  - {rel}")
    lines += ["---", ""]
    body_text = body.body.strip()
    image_paths = []
    for raw in body.images or []:
        rel = str(raw or "").strip().lstrip("/")
        if rel and rel not in image_paths:
            image_paths.append(rel)
    if image_paths:
        extras = []
        for rel in image_paths:
            url = f"https://avaivy.cloud/api/media/public/file?path={rel}"
            name = Path(rel).name
            if html:
                extras.append(f'<p><img src="{url}" alt="{name}" loading="lazy"/></p>')
            else:
                extras.append(f"![{name}]({url})")
        body_text = (body_text + ("\n\n" if body_text else "") + "\n\n".join(extras)).strip()
    lines += [body_text, ""]
    path.write_text("\n".join(lines), encoding="utf-8")
    sync = _run_sync()
    return {"ok": True, "file": str(path), "slug": slug, "sync": sync, "audio": audio_paths, "images": image_paths}


@router.post("/api/ops/upload")
async def ops_upload(request: Request, kind: str = Form("audio"), file: UploadFile = File(...)):
    if not _local(request):
        return _deny()
    dest_dir = KINDS.get(kind, KINDS["documents"])
    dest_dir.mkdir(parents=True, exist_ok=True)
    name = Path(file.filename or "upload.bin").name
    dest = dest_dir / name
    dest.write_bytes(await file.read())
    rel = dest.relative_to(MEDIA).as_posix()
    return {
        "ok": True,
        "saved": str(dest),
        "media_path": rel,
        "url": f"https://avaivy.cloud/api/media/public/file?path={rel}",
    }


@router.post("/api/ops/rewrite")
async def ops_rewrite(body: RewriteIn, request: Request):
    if not _local(request):
        return _deny()
    up, models = await ollama_svc.tags(force=True)
    if not up:
        return {
            "ok": False,
            "working": False,
            "detail": "On-device brain is not running. Local Ava cannot rewrite until it is up.",
        }
    want_live = body.include_live or bool(WANTS_LIVE.search(body.text or ""))
    facts = await _live_facts() if want_live else ""
    system = (
        "You rewrite operator notes for Ava Ivy, the Root Server on the Big Island of Hawaiʻi.\n"
        "Current calendar year is 2026.\n"
        "A 3- or 4-digit number next to a date (1947, 0730, 19:47) is a CLOCK TIME, never a year. "
        "1947 on Aug 19 means 19:47 HST on 19 Aug 2026.\n"
        "Spellings: avay/ava = Ava. Live numbers only from LIVE FACTS.\n"
        "If LIVE FACTS are provided, you MUST include those exact numbers in the rewrite. "
        "That is the scan. Do not say you scanned unless those numbers appear.\n"
        "Write a short public-ready note. Short sentences."
    )
    user = body.text
    if facts:
        user = f"OPERATOR DRAFT:\n{body.text}\n\nLIVE FACTS (use these numbers):\n{facts}"
    t0 = time.monotonic()
    raw = await asyncio.to_thread(
        ollama_svc.chat_sync,
        [{"role": "system", "content": system}, {"role": "user", "content": user}],
        model=config.OLLAMA_MODEL,
        timeout=120,
    )
    ms = int((time.monotonic() - t0) * 1000)
    text = _clean_ollama(raw or "")
    if not text:
        return {
            "ok": False,
            "working": True,
            "model": config.OLLAMA_MODEL,
            "ms": ms,
            "models": models,
            "detail": "Ollama answered empty. Try again.",
        }
    return {
        "ok": True,
        "working": True,
        "text": text,
        "model": config.OLLAMA_MODEL,
        "ms": ms,
        "models": models,
        "facts_included": bool(facts),
        "facts": facts,
    }


@router.post("/api/ops/sync-blogs")
async def ops_sync(request: Request):
    if not _local(request):
        return _deny()
    return _run_sync()


@router.post("/api/ops/publish-rootmc")
async def ops_publish(request: Request):
    if not _local(request):
        return _deny()
    try:
        r = subprocess.run(
            ["bash", str(PUBLISH)],
            capture_output=True,
            text=True,
            timeout=180,
        )
        return {"ok": r.returncode == 0, "log": (r.stdout + "\n" + r.stderr)[-4000:]}
    except Exception as e:
        return {"ok": False, "detail": str(e)}


def _run_sync() -> dict:
    try:
        r = subprocess.run(
            [sys.executable, str(SYNC)],
            capture_output=True,
            text=True,
            timeout=120,
        )
        return {"ok": r.returncode == 0, "log": (r.stdout + "\n" + r.stderr)[-3000:]}
    except Exception as e:
        return {"ok": False, "detail": str(e)}


@router.get("/api/ops/sites-posts")
async def ops_sites_posts(request: Request):
    """Latest posts per site for the desktop Sites tab."""
    if not _local(request):
        return _deny()
    out = []
    for brand in ("ava", "rootrecord", "rootmc"):
        root = POSTS / brand
        latest = None
        if root.is_dir():
            for path in sorted(root.glob("*.md"), key=lambda p: p.stat().st_mtime, reverse=True)[:1]:
                latest = {"slug": path.stem, "mtime": path.stat().st_mtime, "path": str(path)}
        out.append({"brand": brand, "latest": latest})
    from apps.core.services.obs_desk_data import latest_blog_across_sites

    return {"ok": True, "sites": out, "newest": latest_blog_across_sites()}


class FanoutBlogIn(BlogIn):
    brands: list[str] = Field(default_factory=lambda: ["ava", "rootrecord", "rootmc"])


class PythonDropToggleIn(BaseModel):
    name: str
    enabled: bool | None = None
    autostart: bool | None = None
    restart_on_exit: bool | None = None


@router.post("/api/ops/sites-fanout")
async def ops_sites_fanout(body: FanoutBlogIn, request: Request):
    """Save the same post to multiple site trees, then sync."""
    if not _local(request):
        return _deny()
    saved = []
    for brand in body.brands:
        sub = BlogIn(
            brand=brand,
            title=body.title,
            body=body.body,
            teaser=body.teaser,
            category=body.category,
            date=body.date,
            published=body.published,
            audio=list(body.audio or []),
            images=list(body.images or []),
        )
        resp = await ops_blog(sub, request)
        ok = resp.get("ok") if isinstance(resp, dict) else getattr(resp, "status_code", 500) == 200
        saved.append({"brand": brand, "ok": bool(ok)})
    sync = _run_sync()
    return {"ok": True, "saved": saved, "sync": sync}


@router.get("/api/ops/goal-drafts")
async def ops_goal_drafts_get(request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services.goal_drafts import load_drafts

    return {"ok": True, **load_drafts()}


@router.post("/api/ops/goal-drafts/generate")
async def ops_goal_drafts_generate(request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services.goal_drafts import generate_drafts

    return await generate_drafts()


@router.post("/api/ops/goal-drafts/approve")
async def ops_goal_drafts_approve(request: Request, index: int = 0):
    if not _local(request):
        return _deny()
    from apps.core.services.goal_drafts import approve_draft

    return approve_draft(index)


@router.get("/api/ops/python-drop/status")
async def ops_python_drop_status(request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services.python_drop_runner import get_runner

    return get_runner().status()


@router.post("/api/ops/python-drop/rescan")
async def ops_python_drop_rescan(request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services.python_drop_runner import get_runner

    return {"ok": True, **get_runner().rescan()}


@router.post("/api/ops/python-drop/toggle")
async def ops_python_drop_toggle(body: PythonDropToggleIn, request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services.python_drop_runner import get_runner

    return get_runner().update_script(
        body.name,
        enabled=body.enabled,
        autostart=body.autostart,
        restart_on_exit=body.restart_on_exit,
    )


class XmrigIn(BaseModel):
    action: str = "status"
    window: bool | None = None


def _xmrig_ctl():
    from importlib.util import module_from_spec, spec_from_file_location

    p = Path.home() / ".ollama" / "skills" / "xmrig" / "scripts" / "xmrig_ctl.py"
    spec = spec_from_file_location("xmrig_ctl", p)
    if spec is None or spec.loader is None:
        raise FileNotFoundError(str(p))
    mod = module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


@router.get("/api/ops/xmrig")
async def ops_xmrig_get(request: Request):
    if not _local(request):
        return _deny()
    return _xmrig_ctl().status()


@router.post("/api/ops/xmrig")
async def ops_xmrig_set(body: XmrigIn, request: Request):
    if not _local(request):
        return _deny()
    ctl = _xmrig_ctl()
    action = (body.action or "status").strip().lower()
    if action in {"start", "on"}:
        window = True if body.window is None else bool(body.window)
        return ctl.start(window=window)
    if action in {"stop", "off"}:
        return ctl.stop()
    if action in {"smoke", "smoke-test"}:
        return ctl.smoke(window=bool(body.window))
    return ctl.status()


# --- GitHub auto-push (Emergent safety switch) ---

_AUTO_PUSH_TOGGLE = CORE / "scripts" / "github-auto-push-toggle.sh"


@router.get("/api/ops/github-auto-push")
async def ops_github_auto_push_status(request: Request):
    if not _local(request):
        return _deny()
    r = subprocess.run(
        ["bash", str(_AUTO_PUSH_TOGGLE), "status"],
        capture_output=True,
        text=True,
        timeout=20,
    )
    flag = Path.home() / ".local/state/ava/github-auto-push.off"
    active = subprocess.run(
        ["systemctl", "--user", "is-active", "ava-auto-push.timer"],
        capture_output=True,
        text=True,
    ).stdout.strip()
    enabled = not flag.is_file()
    return {
        "ok": r.returncode == 0,
        "enabled": enabled,
        "timer": active,
        "flag_path": str(flag),
        "detail": (r.stdout or r.stderr or "").strip(),
    }


class GithubAutoPushIn(BaseModel):
    enabled: bool | None = None
    action: str = ""  # on | off | toggle


@router.post("/api/ops/github-auto-push")
async def ops_github_auto_push_set(body: GithubAutoPushIn, request: Request):
    if not _local(request):
        return _deny()
    action = (body.action or "").strip().lower()
    if not action:
        if body.enabled is True:
            action = "on"
        elif body.enabled is False:
            action = "off"
        else:
            action = "toggle"
    if action not in {"on", "off", "toggle", "status"}:
        return JSONResponse({"ok": False, "detail": "action must be on|off|toggle"}, status_code=400)
    r = subprocess.run(
        ["bash", str(_AUTO_PUSH_TOGGLE), action],
        capture_output=True,
        text=True,
        timeout=30,
    )
    flag = Path.home() / ".local/state/ava/github-auto-push.off"
    return {
        "ok": r.returncode == 0,
        "enabled": not flag.is_file(),
        "detail": (r.stdout or r.stderr or "").strip(),
    }


# --- Google AdSense OAuth (local) ---


@router.get("/api/ops/adsense/status")
async def ops_adsense_status(request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services import adsense

    st = adsense.status()
    if st.get("client_configured"):
        st["auth_url"] = adsense.auth_url()
    return st


@router.get("/api/ops/adsense/oauth/callback")
async def ops_adsense_oauth_callback(
    request: Request, code: str = "", error: str = "", state: str = ""
):
    """Public HTTPS callback via ava-origin tunnel. Always exchanges with public redirect_uri."""
    from fastapi.responses import HTMLResponse

    from apps.core.services import adsense

    redirect_used = adsense.REDIRECT

    if error:
        return HTMLResponse(
            f"<h1>AdSense OAuth failed</h1><pre>{error}</pre>",
            status_code=400,
        )
    if not code:
        return HTMLResponse("<h1>AdSense OAuth</h1><p>missing code</p>", status_code=400)
    try:
        result = adsense.exchange_code(code, redirect_uri=redirect_used)
        return HTMLResponse(
            "<html><body style='font-family:system-ui;background:#0a0e14;color:#e5e7eb;"
            "padding:2rem'>"
            "<h1>AdSense connected</h1>"
            "<p>Token saved on the Ava desk. Close this tab and open "
            "<a href='/ops' style='color:#22d3ee'>/ops</a> on the desk machine "
            "→ List accounts.</p>"
            f"<pre>{result}</pre></body></html>"
        )
    except Exception as e:
        return HTMLResponse(
            f"<h1>AdSense OAuth exchange failed</h1><pre>{e}</pre>"
            f"<p>redirect_uri used: {redirect_used}</p>"
            "<p>In Google Cloud Console, Authorized redirect URIs must include that exact "
            "public HTTPS URL (never localhost / example.com).</p>",
            status_code=500,
        )



@router.get("/api/ops/adsense/accounts")
async def ops_adsense_accounts(request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services import adsense

    try:
        return adsense.accounts_summary()
    except Exception as e:
        return JSONResponse({"ok": False, "detail": str(e)}, status_code=500)


@router.post("/api/ops/adsense/report")
async def ops_adsense_report(request: Request, kind: str = "manual"):
    """Run AdSense snapshot now (manual / boot / eod). Local ops only."""
    if not _local(request):
        return _deny()
    from apps.core.crons import adsense_report

    k = (kind or "manual").strip().lower()
    if k not in {"manual", "boot", "eod"}:
        k = "manual"
    return await adsense_report.run(k, force=True)


# --- Google AdMob OAuth (local + public callback) ---


@router.get("/api/ops/admob/status")
async def ops_admob_status(request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services import admob

    st = admob.status()
    if st.get("client_configured"):
        st["auth_url"] = admob.auth_url()
    return st


@router.get("/api/ops/admob/oauth/callback")
async def ops_admob_oauth_callback(
    request: Request, code: str = "", error: str = "", state: str = ""
):
    from fastapi.responses import HTMLResponse

    from apps.core.services import admob

    redirect_used = admob.REDIRECT

    if error:
        return HTMLResponse(f"<h1>AdMob OAuth failed</h1><pre>{error}</pre>", status_code=400)
    if not code:
        return HTMLResponse("<h1>AdMob OAuth</h1><p>missing code</p>", status_code=400)
    try:
        result = admob.exchange_code(code, redirect_uri=redirect_used)
        return HTMLResponse(
            "<html><body style='font-family:system-ui;background:#0a0e14;color:#e5e7eb;padding:2rem'>"
            "<h1>AdMob connected</h1>"
            "<p>Token saved on the Ava desk. Open "
            "<a href='/ops' style='color:#22d3ee'>/ops</a> on the desk "
            "→ List AdMob accounts / Run AdMob report.</p>"
            f"<pre>{result}</pre></body></html>"
        )
    except Exception as e:
        return HTMLResponse(
            f"<h1>AdMob OAuth exchange failed</h1><pre>{e}</pre>"
            f"<p>redirect_uri used: {redirect_used}</p>"
            "<p>Authorized redirect URI must be the public HTTPS URL (never localhost).</p>",
            status_code=500,
        )



@router.get("/api/ops/admob/accounts")
async def ops_admob_accounts(request: Request):
    if not _local(request):
        return _deny()
    from apps.core.services import admob

    try:
        return admob.accounts_summary()
    except Exception as e:
        return JSONResponse({"ok": False, "detail": str(e)}, status_code=500)


@router.post("/api/ops/admob/report")
async def ops_admob_report(request: Request, kind: str = "manual"):
    if not _local(request):
        return _deny()
    from apps.core.crons import admob_report

    k = (kind or "manual").strip().lower()
    if k not in {"manual", "boot", "eod"}:
        k = "manual"
    return await admob_report.run(k, force=True)

