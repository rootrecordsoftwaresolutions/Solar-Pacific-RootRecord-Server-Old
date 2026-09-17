#!/usr/bin/env python3
"""Start, stop, status, and smoke-test XMRig. CPU RandomX, 4 threads. No GPU."""
from __future__ import annotations

import json
import os
import shlex
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

SKILL = Path.home() / ".ollama" / "skills" / "xmrig"
RUNTIME = SKILL / "runtime"
BIN = RUNTIME / "xmrig"
CONFIG = RUNTIME / "config.json"
STATE = SKILL / "state"
PID_FILE = STATE / "xmrig.pid"
LOG_FILE = STATE / "xmrig.log"
HTTP_HOST = "127.0.0.1"
HTTP_PORT = 18067
RX_THREADS = (0, 1, 2, 3)
PREP_SH = SKILL / "scripts" / "xmrig_host_prep.sh"
RUN_SH = SKILL / "scripts" / "xmrig_run.sh"
KILL_SH = SKILL / "scripts" / "xmrig_kill.sh"
INSTALL_SH = SKILL / "scripts" / "install_privileges.sh"
SUDOERS_HINT = (
    "pkexec ~/.ollama/skills/xmrig/scripts/install_privileges.sh "
    "(or sudo that script once). Agents need NOPASSWD on the three xmrig helpers."
)


def _now() -> int:
    return int(time.time())


def _read_json(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}
    return data if isinstance(data, dict) else {}


def _mask_user(raw: object) -> str | None:
    text = str(raw or "").strip()
    if not text:
        return None
    if ":" in text:
        coin, rest = text.split(":", 1)
        if len(rest) > 10:
            return f"{coin}:{rest[:6]}…{rest[-4:]}"
    if len(text) > 10:
        return f"{text[:6]}…{text[-4:]}"
    return text


def _pid_alive(pid: int) -> bool:
    if pid <= 0:
        return False
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True


def _cmdline(pid: int) -> str:
    path = Path(f"/proc/{pid}/cmdline")
    try:
        return path.read_bytes().replace(b"\x00", b" ").decode("utf-8", "replace").strip()
    except OSError:
        return ""


def _is_our_proc(pid: int) -> bool:
    cmd = _cmdline(pid)
    marker = str(BIN)
    return marker in cmd and "xmrig" in cmd


def find_pids() -> list[int]:
    found: list[int] = []
    proc = Path("/proc")
    marker = str(BIN)
    if not proc.is_dir():
        return found
    for entry in proc.iterdir():
        if not entry.name.isdigit():
            continue
        pid = int(entry.name)
        try:
            raw = (entry / "cmdline").read_bytes()
        except OSError:
            continue
        cmd = raw.replace(b"\x00", b" ").decode("utf-8", "replace")
        if marker in cmd:
            found.append(pid)
    return sorted(set(found))


def stored_pid() -> int | None:
    try:
        pid = int(PID_FILE.read_text(encoding="utf-8").strip())
    except Exception:
        return None
    if _pid_alive(pid) and _is_our_proc(pid):
        return pid
    return None


def write_pid(pid: int) -> None:
    STATE.mkdir(parents=True, exist_ok=True)
    PID_FILE.write_text(f"{pid}\n", encoding="utf-8")


def _sudo_n(script: Path, timeout: int = 40) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["sudo", "-n", "--", str(script)],
        capture_output=True,
        text=True,
        timeout=timeout,
    )


def _meminfo_hugepages() -> dict[str, str]:
    mem: dict[str, str] = {}
    try:
        for line in Path("/proc/meminfo").read_text(encoding="utf-8").splitlines():
            if line.startswith("HugePages_") or line.startswith("MemAvailable"):
                key, _, val = line.partition(":")
                mem[key.strip()] = val.strip()
    except OSError:
        pass
    for name, path in (
        ("hp2_nr", Path("/sys/kernel/mm/hugepages/hugepages-2048kB/nr_hugepages")),
        ("hp2_free", Path("/sys/kernel/mm/hugepages/hugepages-2048kB/free_hugepages")),
        ("hp1_nr", Path("/sys/kernel/mm/hugepages/hugepages-1048576kB/nr_hugepages")),
        ("hp1_free", Path("/sys/kernel/mm/hugepages/hugepages-1048576kB/free_hugepages")),
    ):
        try:
            mem[name] = path.read_text(encoding="utf-8").strip()
        except OSError:
            mem[name] = "0"
    return mem


def prepare_host() -> dict[str, Any]:
    """Reserve 2MB/1GB pages and load msr. Needs passwordless helper or a terminal sudo."""
    notes: list[str] = []
    root_ok = False
    try:
        r = _sudo_n(PREP_SH)
    except subprocess.TimeoutExpired:
        return {"ok": False, "root_ok": False, "notes": ["prep_timeout"], "hint": SUDOERS_HINT}
    if r.returncode == 0:
        root_ok = True
        notes.append((r.stdout or "").strip()[:800])
    else:
        err = (r.stderr or r.stdout or "").strip()
        notes.append(err[-400:] if err else "sudo_n_failed")
        notes.append("needs_root")
    return {
        "ok": root_ok,
        "root_ok": root_ok,
        "hugepages": _meminfo_hugepages(),
        "notes": notes,
        "hint": None if root_ok else SUDOERS_HINT,
    }


def _term_cmd(title: str, inner: str) -> list[str]:
    term = (
        shutil.which("gnome-terminal")
        or shutil.which("x-terminal-emulator")
        or shutil.which("konsole")
        or shutil.which("xfce4-terminal")
    )
    if not term:
        return ["bash", "-lc", inner]
    if term.endswith("gnome-terminal"):
        return [term, "--title", title, "--", "bash", "-lc", inner]
    if term.endswith("konsole"):
        return [term, "--new-tab", "-p", f"tabtitle={title}", "-e", "bash", "-lc", inner]
    if term.endswith("xfce4-terminal"):
        return [term, "--title", title, "-e", f"bash -lc {shlex.quote(inner)}"]
    return [term, "-T", title, "-e", "bash", "-lc", inner]


def _miner_argv(*, as_root: bool) -> list[str]:
    if as_root:
        return ["sudo", "-n", "--", str(RUN_SH)]
    return [str(BIN), "--config", str(CONFIG)]


def _run_script(*, as_root: bool, prompt_sudo: bool) -> str:
    prep = shlex.quote(str(PREP_SH))
    run = shlex.quote(str(RUN_SH))
    if as_root:
        return f"sudo -n -- {prep}; exec sudo -n -- {run}"
    if prompt_sudo:
        return f"sudo -- {prep} && exec sudo -- {run}"
    quoted_bin = shlex.quote(str(BIN))
    quoted_cfg = shlex.quote(str(CONFIG))
    quoted_cwd = shlex.quote(str(RUNTIME))
    return f"cd {quoted_cwd} && exec {quoted_bin} --config {quoted_cfg}"


def http_summary(timeout: float = 1.5) -> dict[str, Any] | None:
    url = f"http://{HTTP_HOST}:{HTTP_PORT}/1/summary"
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            raw = json.loads(resp.read().decode("utf-8", "replace"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
        return None
    return raw if isinstance(raw, dict) else None


def _hashrate_from_summary(summary: dict[str, Any] | None) -> dict[str, Any] | None:
    if not summary:
        return None
    hs = summary.get("hashrate") if isinstance(summary.get("hashrate"), dict) else {}
    total = hs.get("total") if isinstance(hs.get("total"), list) else []
    threads = hs.get("threads") if isinstance(hs.get("threads"), list) else []
    return {
        "total_10s": total[0] if len(total) > 0 else None,
        "total_60s": total[1] if len(total) > 1 else None,
        "total_15m": total[2] if len(total) > 2 else None,
        "thread_count": len(threads),
    }


def status() -> dict[str, Any]:
    STATE.mkdir(parents=True, exist_ok=True)
    pids = find_pids()
    pid = stored_pid() or (pids[0] if pids else None)
    cfg = _read_json(CONFIG)
    cpu = cfg.get("cpu") if isinstance(cfg.get("cpu"), dict) else {}
    rx = cpu.get("rx")
    pools = cfg.get("pools") if isinstance(cfg.get("pools"), list) else []
    pool0 = pools[0] if pools and isinstance(pools[0], dict) else {}
    summary = http_summary() if pid else None
    out: dict[str, Any] = {
        "ok": True,
        "running": bool(pid),
        "pid": pid,
        "pids": pids,
        "binary": str(BIN),
        "binary_present": BIN.is_file(),
        "config": str(CONFIG),
        "log": str(LOG_FILE),
        "http": f"http://{HTTP_HOST}:{HTTP_PORT}/1/summary",
        "algo": "rx/0",
        "rx_threads": rx if isinstance(rx, list) else list(RX_THREADS),
        "randomx_mode": (cfg.get("randomx") or {}).get("mode") if isinstance(cfg.get("randomx"), dict) else None,
        "asm": cpu.get("asm"),
        "opencl": False,
        "cuda": False,
        "pool_url": pool0.get("url"),
        "pool_user": _mask_user(pool0.get("user")),
        "hashrate": _hashrate_from_summary(summary),
        "http_up": bool(summary),
        "hugepages": _meminfo_hugepages(),
        "msr_dev": Path("/dev/cpu/0/msr").exists(),
    }
    if not BIN.is_file():
        out["ok"] = False
        out["error"] = "binary_missing"
    return out


def start(*, window: bool = True) -> dict[str, Any]:
    STATE.mkdir(parents=True, exist_ok=True)
    if not BIN.is_file():
        return {"ok": False, "error": "binary_missing", "binary": str(BIN)}
    if not CONFIG.is_file():
        return {"ok": False, "error": "config_missing", "config": str(CONFIG)}
    existing = status()
    if existing.get("running"):
        existing["detail"] = "already_running"
        return existing
    prep = prepare_host()
    as_root = bool(prep.get("root_ok"))
    env = os.environ.copy()
    log_handle = LOG_FILE.open("ab")
    try:
        if window and env.get("DISPLAY"):
            inner = _run_script(as_root=as_root, prompt_sudo=not as_root)
            cmd = _term_cmd("Ava XMRig", inner)
            subprocess.Popen(
                cmd,
                cwd=str(RUNTIME),
                env=env,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                start_new_session=True,
                close_fds=True,
            )
        elif as_root:
            subprocess.Popen(
                _miner_argv(as_root=True),
                cwd=str(RUNTIME),
                env=env,
                stdin=subprocess.DEVNULL,
                stdout=log_handle,
                stderr=subprocess.STDOUT,
                start_new_session=True,
                close_fds=True,
            )
        else:
            return {
                "ok": False,
                "error": "needs_root",
                "prepare": prep,
                "hint": SUDOERS_HINT,
            }
    finally:
        log_handle.close()
    pid = None
    for _ in range(40):
        time.sleep(0.25)
        pids = find_pids()
        if pids:
            pid = pids[0]
            write_pid(pid)
            break
    if not pid:
        return {
            "ok": False,
            "error": "spawn_unseen",
            "prepare": prep,
            "as_root": as_root,
            "hint": "Check the Ava XMRig window or state/xmrig.log. Root helper may still be prompting.",
        }
    return {
        "ok": True,
        "action": "start",
        "pid": pid,
        "window": bool(window and env.get("DISPLAY")),
        "as_root": as_root,
        "prepare": prep,
    }


def stop() -> dict[str, Any]:
    pids = find_pids()
    pid = stored_pid()
    if pid and pid not in pids:
        pids.append(pid)
    killed_via = "none"
    try:
        r = _sudo_n(KILL_SH, timeout=20)
        if r.returncode == 0:
            killed_via = "sudo_kill"
    except subprocess.TimeoutExpired:
        killed_via = "sudo_kill_timeout"
    if killed_via != "sudo_kill":
        if not pids:
            PID_FILE.unlink(missing_ok=True)
            return {"ok": True, "action": "stop", "running": False, "detail": "not_running"}
        for target in pids:
            try:
                os.kill(target, signal.SIGTERM)
            except OSError:
                pass
        deadline = time.time() + 8
        while time.time() < deadline:
            alive = [p for p in pids if _pid_alive(p)]
            if not alive:
                break
            time.sleep(0.2)
        for target in pids:
            if _pid_alive(target):
                try:
                    os.kill(target, signal.SIGKILL)
                except OSError:
                    pass
        killed_via = "user_signal"
    PID_FILE.unlink(missing_ok=True)
    still = find_pids()
    return {
        "ok": not still,
        "action": "stop",
        "stopped": pids,
        "running": bool(still),
        "pids": still,
        "via": killed_via,
    }


def install_privileges(*, graphical: bool = True) -> dict[str, Any]:
    """One-shot root: sudoers drop-in + hugepage/msr prep."""
    if not INSTALL_SH.is_file():
        return {"ok": False, "error": "install_script_missing"}
    env = os.environ.copy()
    argv: list[str]
    if graphical and shutil.which("pkexec") and env.get("DISPLAY"):
        argv = ["pkexec", str(INSTALL_SH)]
    else:
        argv = ["sudo", str(INSTALL_SH)]
    try:
        r = subprocess.run(argv, capture_output=True, text=True, timeout=120)
    except subprocess.TimeoutExpired:
        return {"ok": False, "error": "install_timeout", "hint": "Approve the polkit/sudo prompt."}
    return {
        "ok": r.returncode == 0,
        "code": r.returncode,
        "stdout": (r.stdout or "")[-1200:],
        "stderr": (r.stderr or "")[-600:],
        "prepare": prepare_host() if r.returncode == 0 else None,
    }


def smoke(*, window: bool = False, wait_s: float = 12.0) -> dict[str, Any]:
    """Start, confirm the process, sample HTTP if it comes up, then stop."""
    version = subprocess.run(
        [str(BIN), "--version"],
        capture_output=True,
        text=True,
        timeout=8,
    )
    started = start(window=window)
    if not started.get("ok"):
        return {"ok": False, "error": "start_failed", "start": started, "version": (version.stdout or "")[:240]}
    http_body = None
    deadline = time.time() + wait_s
    while time.time() < deadline:
        http_body = http_summary()
        if http_body:
            break
        time.sleep(0.4)
    st = status()
    stopped = stop()
    sock_ok = False
    try:
        with socket.create_connection((HTTP_HOST, HTTP_PORT), timeout=0.3):
            sock_ok = True
    except OSError:
        sock_ok = False
    running_during = bool(st.get("running"))
    return {
        "ok": running_during and bool(stopped.get("ok")),
        "action": "smoke",
        "version": (version.stdout or version.stderr or "").strip().splitlines()[:4],
        "pid_seen": st.get("pid"),
        "rx_threads": st.get("rx_threads"),
        "http_summary": bool(http_body),
        "http_listen_after_stop": sock_ok,
        "hashrate": st.get("hashrate"),
        "stopped": stopped,
        "note": "Init can take several seconds (RandomX dataset). HTTP during smoke is optional.",
    }


def main(argv: list[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    if not args or args[0] in {"status", "--status"}:
        print(json.dumps(status(), indent=2))
        return 0
    cmd = args[0].strip().lower()
    window = "--window" in args
    no_window = "--no-window" in args or "--headless" in args
    if cmd in {"start", "on"}:
        print(json.dumps(start(window=False if no_window else True), indent=2))
        return 0
    if cmd in {"stop", "off"}:
        print(json.dumps(stop(), indent=2))
        return 0
    if cmd in {"smoke", "smoke-test"}:
        print(json.dumps(smoke(window=window and not no_window), indent=2))
        return 0
    if cmd in {"prepare", "hugepages"}:
        print(json.dumps(prepare_host(), indent=2))
        return 0
    if cmd in {"install", "privileges"}:
        print(json.dumps(install_privileges(), indent=2))
        return 0
    print(
        json.dumps(
            {
                "ok": False,
                "error": "unknown_command",
                "use": "status | start [--no-window] | stop | smoke | prepare | install",
            },
            indent=2,
        )
    )
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
