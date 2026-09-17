"""Operator purge — stop Ava / RootRecord stack and keep it off.

Sticky flag: data/state/operator-purge.json  active=true
Disables \\AVA-CORE\\* Task Scheduler tasks so watchdog cannot respawn.
Kills origin, tunnel, Ollama, Desk, Ava pythonw jobs, music players, Discord.

Resume: open Ava Desk again (start_desk / Electron clears the flag).
Manual: pythonw windows\\operator_purge.py --clear

Never powershell.exe. Never touch Cursor / Explorer / system services.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

CREATE_NO_WINDOW = 0x08000000
HOME = Path(os.environ.get("AVA_HOME", str(Path.home() / "ava")))
REPO = HOME
STATE_PATH = HOME / "Data" / "state" / "operator-purge.json"
# Also write under data/ (canonical live tree) so both layouts see it.
STATE_PATH_ALT = REPO / "data" / "state" / "operator-purge.json"
LOG_PATH = REPO / "data" / "logs" / "operator-purge.log"
MY_PID = os.getpid()

AVA_TASKS = (
    r"\AVA-CORE\watchdog",
    r"\AVA-CORE\auto-push",
    r"\AVA-CORE\auto-pull",
    r"\AVA-CORE\site-update",
    r"\AVA-CORE\ava-desk",
)

REPO_MARKERS = (
    str(REPO).lower().replace("/", "\\"),
    r"c:\users\rootr\ava",
)

DISCORD_NAMES = {
    "discord.exe",
    "discordptb.exe",
    "discordcanary.exe",
    "discorddevelopment.exe",
}


def _si() -> subprocess.STARTUPINFO:
    info = subprocess.STARTUPINFO()
    info.dwFlags |= subprocess.STARTF_USESHOWWINDOW
    info.wShowWindow = 0
    return info


def _log(msg: str) -> None:
    line = f"{datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')} {msg}\n"
    try:
        LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
        with LOG_PATH.open("a", encoding="utf-8") as fh:
            fh.write(line)
    except OSError:
        pass


def _write_state(payload: dict) -> None:
    text = json.dumps(payload, indent=2) + "\n"
    for path in (STATE_PATH, STATE_PATH_ALT):
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        except OSError as e:
            _log(f"state write failed {path}: {e}")


def is_active() -> bool:
    for path in (STATE_PATH_ALT, STATE_PATH):
        if not path.is_file():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        if isinstance(data, dict) and data.get("active"):
            return True
    return False


def _schtasks(args: list[str]) -> tuple[int, str]:
    try:
        out = subprocess.run(
            ["schtasks", *args],
            capture_output=True,
            text=True,
            timeout=30,
            creationflags=CREATE_NO_WINDOW,
            startupinfo=_si(),
        )
        detail = (out.stdout or "") + (out.stderr or "")
        return out.returncode, detail.strip()
    except Exception as e:
        return 1, str(e)


def disable_ava_tasks() -> list[str]:
    notes: list[str] = []
    for name in AVA_TASKS:
        code, detail = _schtasks(["/Change", "/TN", name, "/DISABLE"])
        notes.append(f"disable {name} rc={code}" + (f" {detail[:120]}" if detail and code else ""))
        _log(notes[-1])
    return notes


def enable_ava_tasks() -> list[str]:
    notes: list[str] = []
    for name in AVA_TASKS:
        code, detail = _schtasks(["/Change", "/TN", name, "/ENABLE"])
        notes.append(f"enable {name} rc={code}" + (f" {detail[:120]}" if detail and code else ""))
        _log(notes[-1])
    return notes


def _kill_pid(pid: int) -> bool:
    if not pid or pid == MY_PID:
        return False
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
        return True
    except Exception:
        try:
            subprocess.run(
                ["taskkill", "/PID", str(pid), "/T", "/F"],
                capture_output=True,
                timeout=15,
                creationflags=CREATE_NO_WINDOW,
                startupinfo=_si(),
            )
            return True
        except Exception:
            return False


def _cmd_blob(p) -> str:
    parts = p.info.get("cmdline") or []
    exe = str(p.info.get("exe") or "")
    cwd = ""
    try:
        cwd = str(p.cwd() or "")
    except Exception:
        cwd = ""
    return (" ".join(str(x) for x in parts) + " " + exe + " " + cwd).lower().replace("/", "\\")


def _in_ava_tree(blob: str) -> bool:
    return any(m in blob for m in REPO_MARKERS)


def _reason_for(p) -> str | None:
    """Return kill reason or None to keep."""
    name = str(p.info.get("name") or "").lower()
    blob = _cmd_blob(p)

    # Never touch the agent IDE.
    if "cursor" in name or "\\cursor\\" in blob:
        return None

    if name in DISCORD_NAMES:
        return "discord"
    if name == "cloudflared.exe" or name == "cloudflared":
        return "tunnel"
    if name.startswith("ollama"):
        return "ollama"
    if "play_music_bed" in blob or "ava_music_bed" in blob:
        return "music-bed"
    if name in {"wmplayer.exe", "microsoft.media.player.exe"} and (
        "ava" in blob or "music" in blob
    ):
        return "media-player"
    if name in {"ffplay.exe", "mpv.exe"} and _in_ava_tree(blob):
        return "ava-player"

    if "electron" in name and (
        "apps\\desktop" in blob
        or "ava\\apps\\desktop" in blob
        or str(REPO / "apps" / "desktop").lower().replace("/", "\\") in blob
    ):
        return "desk"

    if "uvicorn" in blob and "apps.core" in blob:
        return "origin"
    if "watchdog.py" in blob:
        return "watchdog"
    if "auto-push.py" in blob or "auto-pull.py" in blob or "site-update.py" in blob:
        return "scheduler-script"
    if "operator_purge.py" in blob and p.info.get("pid") != MY_PID:
        return "purge-other"
    if "net_gate" in blob and _in_ava_tree(blob):
        return "net-gate"

    # Any other python/node from the live Ava tree (bots, one-offs).
    if name in {"python.exe", "pythonw.exe", "node.exe"} and _in_ava_tree(blob):
        # Keep this purge process.
        if "operator_purge.py" in blob:
            return None
        return "ava-python" if name.startswith("python") else "ava-node"

    return None


def kill_stack(*, kill_desk: bool = True) -> list[str]:
    try:
        import psutil
    except Exception as e:
        _log(f"psutil missing {e}")
        return [f"error:no_psutil:{e}"]

    killed: list[str] = []
    targets: list[tuple[int, str]] = []
    try:
        for p in list(psutil.process_iter(["pid", "name", "cmdline", "exe"])):
            pid = int(p.info.get("pid") or 0)
            if pid == MY_PID:
                continue
            reason = _reason_for(p)
            if not reason:
                continue
            if reason == "desk" and not kill_desk:
                continue
            targets.append((pid, reason))
    except Exception as e:
        _log(f"iterate failed {e}")
        return [f"error:iterate:{e}"]

    # Origin / tunnel / scripts first; desk last so the UI can still finish IPC.
    order = {
        "origin": 0,
        "tunnel": 1,
        "ollama": 2,
        "music-bed": 3,
        "media-player": 3,
        "ava-player": 3,
        "scheduler-script": 4,
        "watchdog": 4,
        "net-gate": 4,
        "ava-python": 5,
        "ava-node": 5,
        "discord": 6,
        "purge-other": 7,
        "desk": 9,
    }
    targets.sort(key=lambda t: (order.get(t[1], 8), t[0]))

    for pid, reason in targets:
        if _kill_pid(pid):
            killed.append(f"{reason}:{pid}")
            _log(f"killed {reason} pid={pid}")
        else:
            killed.append(f"fail:{reason}:{pid}")
            _log(f"kill fail {reason} pid={pid}")
    return killed


def clear() -> dict:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    payload = {
        "active": False,
        "cleared_at": now,
        "note": "Desk reopen or --clear. AVA-CORE tasks re-enabled; watchdog nudged.",
    }
    _write_state(payload)
    _log("purge CLEARED")
    task_notes = enable_ava_tasks()
    # Nudge watchdog once so origin can come back without waiting a full minute.
    try:
        watchdog = Path.home() / ".ollama" / "skills" / "ecosystem-history" / "scripts" / "windows-watchdog" / "watchdog.py"
        pythonw = REPO / ".venv" / "Scripts" / "pythonw.exe"
        if pythonw.is_file() and watchdog.is_file():
            subprocess.Popen(
                [str(pythonw), str(watchdog)],
                cwd=str(REPO),
                creationflags=CREATE_NO_WINDOW,
                startupinfo=_si(),
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            task_notes.append("watchdog nudged")
    except Exception as e:
        task_notes.append(f"watchdog nudge failed: {e}")
    return {"ok": True, "active": False, "cleared_at": now, "tasks": task_notes}


def clear_if_active() -> dict:
    """Desk / start_desk entry: restore Ava when the operator opens Desk again."""
    if not is_active():
        return {"ok": True, "active": False, "cleared": False}
    result = clear()
    result["cleared"] = True
    return result


def activate() -> dict:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    payload = {
        "active": True,
        "set_at": now,
        "reason": "desk-operator-purge",
        "resume": "Open Ava Desk again (or run the RootRecord Core Ops WatchDog operator_purge.py --clear)",
    }
    _write_state(payload)
    _log("purge ACTIVE")
    task_notes = disable_ava_tasks()
    try:
        sys.path.insert(0, str(REPO))
        from apps.core.services.hybrid_reports import append_hybrid_lifecycle_event

        lifecycle = append_hybrid_lifecycle_event("STOPPED")
        _log(f"lifecycle stop {lifecycle.get('detail')}")
    except Exception as e:
        _log(f"lifecycle stop failed: {e}")
    # Brief pause so Task Scheduler drops running instances before we kill.
    time.sleep(0.4)
    killed = kill_stack(kill_desk=True)
    return {
        "ok": True,
        "active": True,
        "set_at": now,
        "tasks": task_notes,
        "killed": killed,
        "resume": payload["resume"],
    }


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Ava operator purge")
    ap.add_argument("--clear", action="store_true", help="Clear purge flag and re-enable AVA-CORE tasks")
    ap.add_argument("--status", action="store_true", help="Print whether purge is active")
    ap.add_argument("--json", action="store_true", help="Print result as JSON")
    args = ap.parse_args(argv)

    if args.status:
        result = {"ok": True, "active": is_active()}
    elif args.clear:
        result = clear()
    else:
        result = activate()

    text = json.dumps(result, indent=2)
    if args.json or args.status:
        sys.stdout.write(text + "\n")
    else:
        _log(text)
        sys.stdout.write(text + "\n")
    return 0 if result.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
