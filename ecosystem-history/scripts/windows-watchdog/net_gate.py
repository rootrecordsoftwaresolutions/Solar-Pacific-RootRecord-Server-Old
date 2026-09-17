"""Internet gate + host power. Used by windows/watchdog.py (pythonw, every 1 min).

Internet down for DOWN_S → stop origin, Desk, Ollama, cloudflared.
Internet back → allow watchdog to start them again.

This process must keep running (the scheduled watchdog). Do not sleep the PC —
STANDBYIDLE stays 0 so this tick can notice the net is back.

This PC only has the Balanced scheme. Full performance on AC is CPU 100%.
Battery is a lower DC CPU cap. Never powershell.exe.
"""
from __future__ import annotations

import json
import os
import shutil
import socket
import subprocess
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

CREATE_NO_WINDOW = 0x08000000
DOWN_S = 180
HOME = Path(os.environ.get("AVA_HOME", str(Path.home() / "ava")))
REPO = HOME
STATE_PATH = HOME / "Data" / "state" / "net-gate.json"
LOG_PATH = REPO / "data" / "logs" / "net-gate.log"
STARTUP_VOICE = HOME / "Data" / "state" / "startup-voice.json"
WORDS_DIR = REPO / "Media" / "public" / "audio" / "words"
CLIP_NET_DOWN = "phrase_net_down"
CLIP_NET_UP = "phrase_net_up"

ELECTRON = REPO / "apps" / "desktop" / "node_modules" / "electron" / "dist" / "electron.exe"
DESK_DIR = REPO / "apps" / "desktop"
OLLAMA_APP = Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Ollama" / "ollama app.exe"
OLLAMA_EXE = Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Ollama" / "ollama.exe"

MY_PID = os.getpid()


def _si() -> subprocess.STARTUPINFO:
    info = subprocess.STARTUPINFO()
    info.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    info.wShowWindow = 0
    return info


def _log(msg: str) -> None:
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    line = f"{datetime.now(timezone.utc).isoformat()} {msg}\n"
    try:
        with LOG_PATH.open("a", encoding="utf-8") as fh:
            fh.write(line)
    except OSError:
        pass


def _load() -> dict:
    if not STATE_PATH.is_file():
        return {}
    try:
        data = json.loads(STATE_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}
    return data if isinstance(data, dict) else {}


def _save(data: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    data["updated"] = datetime.now(timezone.utc).isoformat()
    STATE_PATH.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def _tcp(host: str, port: int, timeout: float = 2.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def internet_up() -> bool:
    if _tcp("1.1.1.1", 443) or _tcp("8.8.8.8", 443):
        return True
    try:
        with urllib.request.urlopen("http://www.msftconnecttest.com/connecttest.txt", timeout=3) as r:
            return r.status == 200
    except Exception:
        return False


def on_ac() -> bool:
    try:
        import psutil

        batt = psutil.sensors_battery()
        if batt is None:
            return True
        return bool(getattr(batt, "power_plugged", True))
    except Exception:
        return True


def _powercfg(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["powercfg", *args],
        capture_output=True,
        text=True,
        timeout=15,
        creationflags=CREATE_NO_WINDOW,
        startupinfo=_si(),
    )


def apply_power(*, ava_stopped: bool) -> dict:
    """AC = full CPU. Battery = saver caps. Sleep-after stays 0 so this tick still runs."""
    ac = on_ac()
    cpu_max = 100 if ac else (40 if ava_stopped else 70)
    cpu_min = 100 if ac else 5
    monitor_ac = 0
    monitor_dc = 3 if ava_stopped else 5
    st = _load()
    last = st.get("power") or {}
    wanted = {"ac": ac, "cpu_max": cpu_max, "cpu_min": cpu_min, "ava_stopped": ava_stopped}
    if last.get("ac") == ac and last.get("cpu_max") == cpu_max and last.get("ava_stopped") == ava_stopped:
        return {"ok": True, "skipped": True, **wanted}
    try:
        _powercfg("/change", "standby-timeout-ac", "0")
        _powercfg("/change", "standby-timeout-dc", "0")
        _powercfg("/change", "hibernate-timeout-ac", "0")
        _powercfg("/change", "hibernate-timeout-dc", "0")
        _powercfg("/change", "monitor-timeout-ac", str(monitor_ac))
        _powercfg("/change", "monitor-timeout-dc", str(monitor_dc))
        _powercfg(
            "/setacvalueindex", "SCHEME_CURRENT", "SUB_PROCESSOR", "PROCTHROTTLEMAX", "100"
        )
        _powercfg(
            "/setacvalueindex", "SCHEME_CURRENT", "SUB_PROCESSOR", "PROCTHROTTLEMIN", "100"
        )
        _powercfg(
            "/setdcvalueindex", "SCHEME_CURRENT", "SUB_PROCESSOR", "PROCTHROTTLEMAX", str(cpu_max)
        )
        _powercfg(
            "/setdcvalueindex", "SCHEME_CURRENT", "SUB_PROCESSOR", "PROCTHROTTLEMIN", str(cpu_min)
        )
        _powercfg("/setactive", "SCHEME_CURRENT")
    except Exception as e:
        _log(f"powercfg failed {e}")
        return {"ok": False, "detail": str(e)[:200], **wanted}
    st["power"] = wanted
    _save(st)
    _log(f"power ac={ac} cpu_max_dc={cpu_max} ava_stopped={ava_stopped}")
    return {"ok": True, **wanted}


def _cmd(p) -> str:
    try:
        parts = p.info.get("cmdline") or []
        return " ".join(str(x) for x in parts)
    except Exception:
        return ""


def _name(p) -> str:
    return str(p.info.get("name") or "").lower()


def desk_running() -> bool:
    try:
        import psutil
    except Exception:
        return False
    desk = str(DESK_DIR).lower()
    electron = str(ELECTRON).lower()
    for p in psutil.process_iter(["pid", "name", "cmdline", "exe"]):
        if "--type=" in _cmd(p).lower():
            continue
        exe = str(p.info.get("exe") or "").lower()
        cmd = _cmd(p).lower()
        if electron in exe or electron in cmd or (desk in cmd and "electron" in _name(p)):
            return True
    return False


def ollama_running() -> bool:
    try:
        import psutil
    except Exception:
        return False
    for p in psutil.process_iter(["name"]):
        n = str(p.info.get("name") or "").lower()
        if n.startswith("ollama"):
            return True
    return False


def _kill_pid(pid: int) -> None:
    if not pid or pid == MY_PID:
        return
    try:
        import psutil

        proc = psutil.Process(pid)
        for child in proc.children(recursive=True):
            if child.pid != MY_PID:
                try:
                    child.kill()
                except Exception:
                    pass
        proc.kill()
    except Exception:
        subprocess.run(
            ["taskkill", "/PID", str(pid), "/T", "/F"],
            capture_output=True,
            timeout=10,
            creationflags=CREATE_NO_WINDOW,
            startupinfo=_si(),
        )


def stop_ava() -> dict:
    """Stop origin, Desk, Ollama, tunnel. Leave Task Scheduler watchdog alive."""
    killed: list[str] = []
    try:
        import psutil
    except Exception as e:
        _log(f"psutil missing {e}")
        return {"ok": False, "detail": "no_psutil"}

    desk_was = desk_running()
    ollama_was = ollama_running()

    for p in list(psutil.process_iter(["pid", "name", "cmdline", "exe"])):
        pid = int(p.info.get("pid") or 0)
        if pid == MY_PID:
            continue
        name = _name(p)
        cmd = _cmd(p).lower()
        exe = str(p.info.get("exe") or "").lower()
        reason = ""
        if "apps.core.main" in cmd or ("uvicorn" in cmd and "apps.core" in cmd):
            reason = "origin"
        elif name == "cloudflared.exe" or name == "cloudflared":
            reason = "tunnel"
        elif name.startswith("ollama"):
            reason = "ollama"
        elif "electron" in name and (
            "apps\\desktop" in cmd or "apps\\desktop" in exe or str(ELECTRON).lower() in exe
        ):
            reason = "desk"
        if not reason:
            continue
        _kill_pid(pid)
        killed.append(f"{reason}:{pid}")

    _note_origin_down()
    _log("stopped " + (", ".join(killed) if killed else "nothing-listed"))
    return {
        "ok": True,
        "killed": killed,
        "desk_was_open": desk_was,
        "ollama_was_up": ollama_was,
    }


def _ffplay() -> list[str] | None:
    found = shutil.which("ffplay")
    if found:
        return [found, "-nodisp", "-autoexit", "-loglevel", "quiet"]
    try:
        import imageio_ffmpeg

        ff = imageio_ffmpeg.get_ffmpeg_exe()
        if ff:
            sib = Path(ff).parent / ("ffplay.exe" if os.name == "nt" else "ffplay")
            if sib.is_file():
                return [str(sib), "-nodisp", "-autoexit", "-loglevel", "quiet"]
    except Exception:
        pass
    return None


def play_phrase(name: str) -> bool:
    """Play one words/*.mp3 without origin. Not for Starlink/recycle (never phrase_device_startup)."""
    path = WORDS_DIR / f"{name}.mp3"
    if not path.is_file():
        _log(f"voice clip missing {name}")
        return False
    cmd = _ffplay()
    if not cmd:
        _log(f"voice clip no ffplay for {name}")
        return False
    try:
        subprocess.Popen(
            cmd + [str(path)],
            cwd=str(REPO),
            creationflags=CREATE_NO_WINDOW,
            startupinfo=_si(),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        _log(f"voice clip {name}")
        return True
    except Exception as e:
        _log(f"voice clip failed {name}: {e}")
        return False


def _note_origin_down() -> None:
    now = time.time()
    payload = {}
    if STARTUP_VOICE.is_file():
        try:
            payload = json.loads(STARTUP_VOICE.read_text(encoding="utf-8"))
        except Exception:
            payload = {}
    if not isinstance(payload, dict):
        payload = {}
    payload["last_seen_down_at"] = now
    payload["last_seen_down_iso"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    STARTUP_VOICE.parent.mkdir(parents=True, exist_ok=True)
    try:
        STARTUP_VOICE.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    except OSError:
        pass


def start_ollama() -> None:
    if ollama_running():
        return
    app = OLLAMA_APP if OLLAMA_APP.is_file() else OLLAMA_EXE
    if not app.is_file():
        return
    try:
        subprocess.Popen(
            [str(app)],
            cwd=str(app.parent),
            creationflags=CREATE_NO_WINDOW,
            startupinfo=_si(),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        _log(f"started ollama {app.name}")
    except Exception as e:
        _log(f"ollama start failed {e}")


def start_desk() -> None:
    """Visible Ava Desk. Never CREATE_NO_WINDOW — that is a GUI, not a console."""
    if desk_running() or not ELECTRON.is_file():
        return
    env = os.environ.copy()
    env["AVA_HOME"] = str(REPO)
    env["AVA_HANDOFF"] = str(REPO)
    env["AVA_ENV_FILE"] = str(REPO / ".env")
    try:
        si = subprocess.STARTUPINFO()
        si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        si.wShowWindow = 1  # SW_SHOWNORMAL — this is a GUI
        subprocess.Popen(
            [str(ELECTRON), "."],
            cwd=str(DESK_DIR),
            env=env,
            startupinfo=si,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        _log("started desk")
    except Exception as e:
        _log(f"desk start failed {e}")


def tick(*, online: bool, origin_up: bool) -> dict:
    """One watchdog pass. Returns allow_origin / restart_desk."""
    # Sticky operator purge (Clear desk button) — never auto-restore until --clear.
    try:
        import operator_purge

        if operator_purge.is_active():
            apply_power(ava_stopped=True)
            return {
                "allow_origin": False,
                "restart_desk": False,
                "online": bool(online),
                "stopped": True,
                "operator_purge": True,
            }
    except Exception:
        pass

    now = time.time()
    st = _load()
    stopped = bool(st.get("ava_stopped"))
    st["origin_seen"] = bool(origin_up)

    if online:
        down_for = 0.0
        if st.get("down_since"):
            try:
                down_for = max(0.0, now - float(st["down_since"]))
            except (TypeError, ValueError):
                down_for = 0.0
        st["down_since"] = None
        st["online"] = True
        restart_desk = False
        if stopped:
            if st.get("ollama_was_up"):
                start_ollama()
            restart_desk = bool(st.get("desk_was_open"))
            st["ava_stopped"] = False
            st["restored_at"] = datetime.now(timezone.utc).isoformat()
            _log(f"internet back after_down_s={int(down_for)}")
            play_phrase(CLIP_NET_UP)
        _save(st)
        apply_power(ava_stopped=False)
        return {"allow_origin": True, "restart_desk": restart_desk, "online": True, "stopped": False}

    if not st.get("down_since"):
        st["down_since"] = now
        st["online"] = False
        _save(st)
        _log("internet down — timer started")
        apply_power(ava_stopped=stopped)
        return {"allow_origin": True, "restart_desk": False, "online": False, "stopped": stopped}

    try:
        down_for = now - float(st["down_since"])
    except (TypeError, ValueError):
        st["down_since"] = now
        _save(st)
        return {"allow_origin": True, "restart_desk": False, "online": False, "stopped": stopped}

    if down_for < DOWN_S:
        apply_power(ava_stopped=stopped)
        _save(st)
        return {"allow_origin": True, "restart_desk": False, "online": False, "stopped": stopped}

    if not stopped:
        play_phrase(CLIP_NET_DOWN)
        result = stop_ava()
        st["ava_stopped"] = True
        st["desk_was_open"] = bool(result.get("desk_was_open"))
        st["ollama_was_up"] = bool(result.get("ollama_was_up"))
        st["stopped_at"] = datetime.now(timezone.utc).isoformat()
        st["online"] = False
        _save(st)
        apply_power(ava_stopped=True)
        _log(f"ava stopped after_down_s={int(down_for)}")
        return {"allow_origin": False, "restart_desk": False, "online": False, "stopped": True}

    apply_power(ava_stopped=True)
    st["online"] = False
    _save(st)
    return {"allow_origin": False, "restart_desk": False, "online": False, "stopped": True}
