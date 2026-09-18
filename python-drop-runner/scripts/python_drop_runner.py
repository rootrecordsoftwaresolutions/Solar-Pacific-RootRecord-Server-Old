"""Drop-in Python runner: always-on scripts + timed schedule folders.

Layout under AUTOMATION_DROP_DIR (default python-drop-runner/drop):

  *.py                          always-on (terminal window, restart)
  on-time/HH:MM/*.py            once at that Hawaiian clock time (every 5 min slots)
  Every 5 Mins/*.py             on the 5-minute marks (:00,:05,…)
  Every 15 minutes/*.py         :00,:15,:30,:45
  Every 30 minutes/*.py         :00,:30
  Every Hour/*.py               :00

Timed jobs run headless once per slot; stdout goes to drop/logs/.
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import shlex
import signal
import shutil
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from apps.core import config

log = logging.getLogger("ava.python_drop_runner")

HST = ZoneInfo("Pacific/Honolulu")
CONFIG_NAME = "python-script-autostart.json"
FIRE_STATE_NAME = "timed-fire-state.json"
SHORT_RUN_S = 15
MAX_SHORT_RESTARTS = 2

# Interval folder names (exact — operator-facing).
INTERVAL_DIRS: dict[str, str] = {
    "Every 5 Mins": "5m",
    "Every 15 minutes": "15m",
    "Every 30 minutes": "30m",
    "Every Hour": "1h",
}
ON_TIME_DIR = "on-time"


def _now() -> int:
    return int(time.time())


def _hst_now() -> datetime:
    return datetime.now(HST)


def _slot_key(dt: datetime) -> str:
    # Floor to 5 minutes
    m = (dt.minute // 5) * 5
    return f"{dt.hour:02d}:{m:02d}"


def _all_clock_slots() -> list[str]:
    return [f"{h:02d}:{m:02d}" for h in range(24) for m in range(0, 60, 5)]


def _term_cmd(title: str, script: Path) -> list[str]:
    quoted = shlex.quote(str(script))
    run = (
        f"cd {shlex.quote(str(script.parent))} && "
        f"python3 {quoted}; "
        f"rc=$?; "
        f"echo; echo '[ava] process exited with code' $rc; "
        f"echo '[ava] window auto-closes in 8s'; sleep 8"
    )
    term = (
        shutil.which("x-terminal-emulator")
        or shutil.which("gnome-terminal")
        or shutil.which("konsole")
        or shutil.which("xfce4-terminal")
    )
    if not term:
        return ["bash", "-lc", run]
    if term.endswith("gnome-terminal"):
        return [term, "--title", title, "--", "bash", "-lc", run]
    if term.endswith("konsole"):
        return [term, "--new-tab", "-p", f"tabtitle={title}", "-e", "bash", "-lc", run]
    if term.endswith("xfce4-terminal"):
        return [term, "--title", title, "-e", f"bash -lc {shlex.quote(run)}"]
    return [term, "-T", title, "-e", "bash", "-lc", run]


@dataclass
class ProcState:
    proc: asyncio.subprocess.Process
    started_at: float
    restarts_short: int = 0


class PythonDropRunner:
    def __init__(self) -> None:
        self.drop_dir = Path(config.AUTOMATION_DROP_DIR)
        self.cfg_path = self.drop_dir / CONFIG_NAME
        self.fire_path = self.drop_dir / FIRE_STATE_NAME
        self.log_dir = self.drop_dir / "logs"
        self._task: asyncio.Task | None = None
        self._stop = asyncio.Event()
        self._procs: dict[str, ProcState] = {}
        self._timed_procs: dict[str, asyncio.subprocess.Process] = {}

    def ensure_bootstrap(self) -> None:
        """Create always-on root + every 5-min clock folder + interval folders."""
        self.drop_dir.mkdir(parents=True, exist_ok=True)
        self.log_dir.mkdir(parents=True, exist_ok=True)
        on_time = self.drop_dir / ON_TIME_DIR
        on_time.mkdir(parents=True, exist_ok=True)
        for slot in _all_clock_slots():
            (on_time / slot).mkdir(parents=True, exist_ok=True)
        guide = on_time / "README.txt"
        if not guide.is_file():
            guide.write_text(
                "Drop a .py into HH:MM (every 5 minutes, 24h Hawaiian time). "
                "It runs once at that clock time.\n"
                "Example: on-time/18:15/report.py\n",
                encoding="utf-8",
            )
        for name, kind in INTERVAL_DIRS.items():
            d = self.drop_dir / name
            d.mkdir(parents=True, exist_ok=True)
            readme = d / "README.txt"
            if not readme.is_file():
                readme.write_text(
                    f"Drop .py here to run on the {kind} marks (Hawaiian Standard Time).\n",
                    encoding="utf-8",
                )
        if not self.cfg_path.exists():
            self._write_cfg({"version": 2, "scripts": {}, "updated_at": _now()})
        if not self.fire_path.exists():
            self._write_fire({"version": 1, "fired": {}})

    def _read_cfg(self) -> dict:
        self.ensure_bootstrap()
        try:
            return json.loads(self.cfg_path.read_text(encoding="utf-8"))
        except Exception:
            data = {"version": 2, "scripts": {}, "updated_at": _now()}
            self._write_cfg(data)
            return data

    def _write_cfg(self, data: dict) -> None:
        data["updated_at"] = _now()
        self.cfg_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

    def _read_fire(self) -> dict:
        try:
            return json.loads(self.fire_path.read_text(encoding="utf-8"))
        except Exception:
            return {"version": 1, "fired": {}}

    def _write_fire(self, data: dict) -> None:
        self.fire_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

    def _discover(self, cfg: dict) -> dict:
        """Always-on: only top-level drop/*.py (not timed folders)."""
        scripts = dict(cfg.get("scripts") or {})
        py_files = sorted(p for p in self.drop_dir.glob("*.py") if p.is_file())
        seen = set()
        for p in py_files:
            key = p.name
            seen.add(key)
            if key not in scripts:
                scripts[key] = {
                    "enabled": True,
                    "autostart": True,
                    "window": True,
                    "restart_on_exit": True,
                    "short_restart_limit": MAX_SHORT_RESTARTS,
                    "short_restarts": 0,
                    "disabled_reason": "",
                    "last_start_ts": None,
                    "last_exit_ts": None,
                    "last_exit_code": None,
                }
        for key in list(scripts.keys()):
            if key not in seen:
                scripts[key]["enabled"] = False
                scripts[key]["disabled_reason"] = "file_missing"
        cfg["scripts"] = scripts
        self._write_cfg(cfg)
        return cfg

    def list_timed_scripts(self) -> dict[str, list[str]]:
        """Relative paths under each schedule bucket."""
        self.ensure_bootstrap()
        out: dict[str, list[str]] = {}
        on_time = self.drop_dir / ON_TIME_DIR
        for slot_dir in sorted(on_time.iterdir() if on_time.is_dir() else []):
            if not slot_dir.is_dir() or not re.fullmatch(r"\d{2}:\d{2}", slot_dir.name):
                continue
            pys = sorted(p.name for p in slot_dir.glob("*.py") if p.is_file())
            if pys:
                out[f"{ON_TIME_DIR}/{slot_dir.name}"] = pys
        for name in INTERVAL_DIRS:
            d = self.drop_dir / name
            pys = sorted(p.name for p in d.glob("*.py") if p.is_file()) if d.is_dir() else []
            if pys:
                out[name] = pys
        return out

    def rescan(self) -> dict:
        return self._discover(self._read_cfg())

    def status(self) -> dict:
        cfg = self.rescan()
        scripts = dict(cfg.get("scripts") or {})
        running = {
            name: {
                "pid": st.proc.pid,
                "started_at": int(st.started_at),
                "alive": st.proc.returncode is None,
                "short_restarts_live": st.restarts_short,
            }
            for name, st in self._procs.items()
        }
        now = _hst_now()
        return {
            "ok": True,
            "drop_dir": str(self.drop_dir),
            "config_path": str(self.cfg_path),
            "scripts": scripts,
            "running": running,
            "timed": self.list_timed_scripts(),
            "clock_hst": now.strftime("%Y-%m-%d %H:%M:%S %Z"),
            "current_slot": _slot_key(now),
            "folders": {
                "on_time_slots": 288,
                "intervals": list(INTERVAL_DIRS.keys()),
            },
        }

    def update_script(
        self,
        name: str,
        *,
        enabled: bool | None = None,
        autostart: bool | None = None,
        restart_on_exit: bool | None = None,
    ) -> dict:
        cfg = self.rescan()
        scripts = dict(cfg.get("scripts") or {})
        if name not in scripts:
            return {"ok": False, "detail": "script_not_found", "name": name}
        row = dict(scripts[name])
        if enabled is not None:
            row["enabled"] = bool(enabled)
            if enabled:
                row["disabled_reason"] = ""
                row["short_restarts"] = 0
        if autostart is not None:
            row["autostart"] = bool(autostart)
        if restart_on_exit is not None:
            row["restart_on_exit"] = bool(restart_on_exit)
        scripts[name] = row
        cfg["scripts"] = scripts
        self._write_cfg(cfg)
        return {"ok": True, "script": {name: row}}

    def _due_timed_paths(self, now: datetime) -> list[tuple[str, Path]]:
        """Return (fire_id, script_path) due at this HST clock."""
        due: list[tuple[str, Path]] = []
        slot = _slot_key(now)
        day = now.strftime("%Y-%m-%d")
        minute = now.minute
        hour_mark = minute == 0
        five_mark = minute % 5 == 0
        fifteen_mark = minute in (0, 15, 30, 45)
        thirty_mark = minute in (0, 30)

        # Exact clock folder
        clock_dir = self.drop_dir / ON_TIME_DIR / slot
        if clock_dir.is_dir():
            for p in sorted(clock_dir.glob("*.py")):
                if p.is_file():
                    fid = f"{day}|on-time|{slot}|{p.name}"
                    due.append((fid, p))

        checks = [
            ("Every 5 Mins", five_mark),
            ("Every 15 minutes", fifteen_mark),
            ("Every 30 minutes", thirty_mark),
            ("Every Hour", hour_mark),
        ]
        for folder, hit in checks:
            if not hit:
                continue
            d = self.drop_dir / folder
            if not d.is_dir():
                continue
            for p in sorted(d.glob("*.py")):
                if p.is_file():
                    fid = f"{day}|{folder}|{slot}|{p.name}"
                    due.append((fid, p))
        return due

    async def _run_timed_once(self, fire_id: str, script: Path) -> None:
        """Headless one-shot; log to drop/logs/."""
        safe = re.sub(r"[^\w.\-]+", "_", fire_id)[:120]
        log_path = self.log_dir / f"{safe}.log"
        env = os.environ.copy()
        env["AVA_DROP_FIRE_ID"] = fire_id
        env["AVA_DROP_SLOT"] = _slot_key(_hst_now())
        try:
            with log_path.open("a", encoding="utf-8") as lf:
                lf.write(f"\n--- {_hst_now().isoformat()} start {script} ---\n")
                lf.flush()
                proc = await asyncio.create_subprocess_exec(
                    "python3",
                    str(script),
                    cwd=str(script.parent),
                    env=env,
                    stdout=lf,
                    stderr=asyncio.subprocess.STDOUT,
                    start_new_session=True,
                )
            self._timed_procs[fire_id] = proc
            log.info("timed drop start %s pid=%s", fire_id, proc.pid)
            # Don't block the loop forever — reap later
            asyncio.create_task(self._reap_timed(fire_id, proc, log_path))
        except Exception as e:
            log.warning("timed drop spawn failed %s: %s", script, e)
            try:
                log_path.write_text(
                    log_path.read_text(encoding="utf-8", errors="replace")
                    + f"\nspawn_failed: {e}\n",
                    encoding="utf-8",
                )
            except Exception:
                pass

    async def _reap_timed(
        self, fire_id: str, proc: asyncio.subprocess.Process, log_path: Path
    ) -> None:
        try:
            rc = await asyncio.wait_for(proc.wait(), timeout=3600)
        except asyncio.TimeoutError:
            try:
                proc.send_signal(signal.SIGTERM)
            except Exception:
                pass
            rc = -9
            log.warning("timed drop timeout %s", fire_id)
        self._timed_procs.pop(fire_id, None)
        try:
            with log_path.open("a", encoding="utf-8") as lf:
                lf.write(f"--- exit {rc} ---\n")
        except Exception:
            pass
        log.info("timed drop done %s rc=%s", fire_id, rc)

    async def _tick_timed(self) -> None:
        now = _hst_now()
        # Fire in the first ~50s of a mark minute so we don't miss the window.
        if now.second > 50:
            return
        # Only meaningful on 5-minute boundaries (covers all interval folders too).
        if now.minute % 5 != 0:
            return

        fire = self._read_fire()
        fired = dict(fire.get("fired") or {})
        due = self._due_timed_paths(now)
        changed = False
        for fid, path in due:
            if fired.get(fid):
                continue
            # mark before spawn so a crash doesn't double-fire
            fired[fid] = int(time.time())
            changed = True
            await self._run_timed_once(fid, path)
        if changed:
            # prune old fire ids (keep ~3 days)
            cutoff = time.time() - 3 * 86400
            fired = {k: v for k, v in fired.items() if int(v or 0) >= cutoff}
            fire["fired"] = fired
            self._write_fire(fire)

    async def _spawn(self, script: Path, meta: dict) -> asyncio.subprocess.Process | None:
        title = f"Ava Script: {script.name}"
        cmd = _term_cmd(title, script)
        env = os.environ.copy()
        try:
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                cwd=str(script.parent),
                env=env,
                start_new_session=True,
            )
            meta["last_start_ts"] = _now()
            log.info("started drop script %s pid=%s", script.name, proc.pid)
            return proc
        except Exception as e:
            meta["last_exit_code"] = -1
            meta["disabled_reason"] = f"spawn_failed:{e}"
            log.warning("spawn failed %s: %s", script, e)
            return None

    async def _stop_one(self, name: str) -> None:
        st = self._procs.get(name)
        if not st:
            return
        try:
            st.proc.send_signal(signal.SIGTERM)
        except Exception:
            pass
        self._procs.pop(name, None)

    async def _loop(self) -> None:
        self.ensure_bootstrap()
        while not self._stop.is_set():
            cfg = self._discover(self._read_cfg())
            scripts: dict = cfg.get("scripts") or {}
            for name, meta in scripts.items():
                script = self.drop_dir / name
                enabled = bool(meta.get("enabled", True) and meta.get("autostart", True))
                cur = self._procs.get(name)
                if not enabled:
                    if cur:
                        await self._stop_one(name)
                    continue
                if not script.exists():
                    continue
                if not cur:
                    proc = await self._spawn(script, meta)
                    if proc is not None:
                        self._procs[name] = ProcState(
                            proc=proc,
                            started_at=time.time(),
                            restarts_short=int(meta.get("short_restarts") or 0),
                        )
                    continue
                if cur.proc.returncode is None:
                    continue
                runtime = time.time() - cur.started_at
                meta["last_exit_ts"] = _now()
                meta["last_exit_code"] = cur.proc.returncode
                self._procs.pop(name, None)
                if runtime < SHORT_RUN_S:
                    cur.restarts_short += 1
                    meta["short_restarts"] = cur.restarts_short
                else:
                    meta["short_restarts"] = 0
                    cur.restarts_short = 0
                limit = int(meta.get("short_restart_limit") or MAX_SHORT_RESTARTS)
                if int(meta.get("short_restarts") or 0) > limit:
                    meta["enabled"] = False
                    meta["disabled_reason"] = "too_many_short_restarts"
                    log.warning("auto-disabled %s after short restarts", name)
                    continue
                if bool(meta.get("restart_on_exit", True)):
                    proc = await self._spawn(script, meta)
                    if proc is not None:
                        self._procs[name] = ProcState(
                            proc=proc,
                            started_at=time.time(),
                            restarts_short=int(meta.get("short_restarts") or 0),
                        )
            self._write_cfg(cfg)
            try:
                await self._tick_timed()
            except Exception:
                log.exception("timed drop tick failed")
            try:
                await asyncio.wait_for(self._stop.wait(), timeout=4.0)
            except asyncio.TimeoutError:
                pass

    def start(self) -> None:
        if self._task and not self._task.done():
            return
        self._stop.clear()
        self._task = asyncio.create_task(self._loop(), name="python-drop-runner")

    async def stop(self) -> None:
        self._stop.set()
        if self._task:
            try:
                await asyncio.wait_for(self._task, timeout=5.0)
            except Exception:
                self._task.cancel()
        for name in list(self._procs.keys()):
            await self._stop_one(name)


_runner = PythonDropRunner()


def get_runner() -> PythonDropRunner:
    return _runner


def ensure_running() -> None:
    _runner.ensure_bootstrap()
    _runner.start()


def main() -> int:
    """CLI: bootstrap folders / print status."""
    import argparse

    p = argparse.ArgumentParser(description="Python drop runner")
    p.add_argument("--bootstrap", action="store_true", help="Create all time folders")
    p.add_argument("--status", action="store_true")
    args = p.parse_args()
    r = get_runner()
    if args.bootstrap or not args.status:
        r.ensure_bootstrap()
        print(f"drop_dir={r.drop_dir}")
        print(f"on-time slots={len(_all_clock_slots())}")
        print("intervals=" + ", ".join(INTERVAL_DIRS))
    if args.status:
        print(json.dumps(r.status(), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
