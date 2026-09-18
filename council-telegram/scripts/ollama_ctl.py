"""Start/stop Ollama aligned with Ava-Core idle-stop.

Holdoff: prefer scripts/idle-stop.sh. Resume only while AVA Console is up.
ensure-ava-runtime is a health check — it will not start origin or Ollama
when the terminal is closed.
"""
from __future__ import annotations

import json
import os
import signal
import subprocess
import time
import urllib.request
from pathlib import Path

from .config import Config

AVA_CORE_ROOT = Path("/home/rootrecord/.ollama/skills/origin")
IDLE_STOP = Path.home() / ".ollama" / "skills" / "idle-stop" / "scripts" / "idle-stop.sh"
if not IDLE_STOP.is_file():
    IDLE_STOP = AVA_CORE_ROOT / "scripts" / "idle-stop.sh"
ENSURE_RUNTIME = Path.home() / ".ollama" / "skills" / "ensure-ava-runtime" / "scripts" / "ensure-ava-runtime.sh"
if not ENSURE_RUNTIME.is_file():
    ENSURE_RUNTIME = AVA_CORE_ROOT / "scripts" / "ensure-ava-runtime.sh"


def is_up(cfg: Config, timeout: float = 2.0) -> bool:
    url = cfg.ollama_base.rstrip("/") + "/api/tags"
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return 200 <= getattr(resp, "status", 200) < 300
    except Exception:  # noqa: BLE001
        return False


def flm_url() -> str:
    return (os.getenv("AVA_FLM_URL") or "http://127.0.0.1:52625").rstrip("/")


def flm_is_up(timeout: float = 1.5) -> bool:
    try:
        with urllib.request.urlopen(flm_url() + "/v1/models", timeout=timeout) as resp:
            return 200 <= getattr(resp, "status", 200) < 300
    except Exception:  # noqa: BLE001
        return False


def npu_chat_enabled() -> bool:
    return os.getenv("AVA_NPU_CHAT", "1").strip().lower() not in {"0", "false", "off", "no"}


def voices_up(cfg: Config, timeout: float = 2.0) -> bool:
    """True when everyday council chat can run.

    NPU chat uses FastFlowLM (:52625). Ollama GGUF may be down on purpose —
    do not treat that as voices asleep.
    """
    if npu_chat_enabled() and flm_is_up(timeout=min(timeout, 1.5)):
        return True
    return is_up(cfg, timeout=timeout)


def flm_stop() -> bool:
    """Unmap FastFlowLM (~9–11G RSS). Does not stop origin or council."""
    try:
        subprocess.run(
            ["systemctl", "--user", "stop", "ava-flm.service"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        pass
    for pid in _pids_matching("flm-real serve"):
        try:
            os.kill(pid, signal.SIGTERM)
        except (ProcessLookupError, PermissionError):
            pass
    for _ in range(24):
        if not flm_is_up(timeout=0.8) and not _pids_matching("flm-real serve"):
            return True
        time.sleep(0.25)
    for pid in _pids_matching("flm-real serve"):
        try:
            os.kill(pid, signal.SIGKILL)
        except (ProcessLookupError, PermissionError):
            pass
    return not flm_is_up(timeout=0.8)


def console_is_up() -> bool:
    state = Path(os.getenv("AVA_STATE_DIR") or (Path.home() / ".ollama" / "skills" / "state" / "store"))
    return (state / "ava-console-up").is_file()


def flm_start(timeout: float = 180) -> bool:
    """One FastFlowLM via ava-flm.service. Drop GGUF first so 16 GB can hold the NPU map."""
    if not console_is_up():
        print("flm start: AVA Console is closed", flush=True)
        return False
    if flm_is_up():
        return True
    try:
        from . import ollama_client
        from .config import load_config

        ollama_client.unload_all(load_config())
    except Exception:
        print("flm start: gguf unload skip", flush=True)
    try:
        subprocess.run(
            ["systemctl", "--user", "reset-failed", "ava-flm.service"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=15,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        pass
    try:
        subprocess.run(
            ["systemctl", "--user", "start", "ava-flm.service"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        pass
    deadline = time.time() + max(5.0, float(timeout))
    while time.time() < deadline:
        if flm_is_up():
            return True
        time.sleep(0.5)
    print("flm start: NPU server still down after wait", flush=True)
    return flm_is_up()


def _pids_matching(needle: str) -> list[int]:
    pids: list[int] = []
    try:
        out = subprocess.check_output(["ps", "-eo", "pid,args"], text=True, errors="replace")
    except (OSError, subprocess.CalledProcessError):
        return pids
    for line in out.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split(None, 1)
        if len(parts) < 2:
            continue
        try:
            pid = int(parts[0])
        except ValueError:
            continue
        args = parts[1]
        if needle in args and "grep" not in args:
            pids.append(pid)
    return pids


def _fuser_kill_11434() -> None:
    try:
        subprocess.run(
            ["fuser", "-k", "11434/tcp"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
    except OSError:
        pass


def stop(cfg: Config) -> bool:
    """Stop Ollama the Ava way when possible."""
    if IDLE_STOP.is_file():
        try:
            subprocess.run(
                ["bash", str(IDLE_STOP)],
                cwd=str(AVA_CORE_ROOT),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=60,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            pass
        time.sleep(0.5)
        if not is_up(cfg, timeout=1.0):
            return True

    for pid in _pids_matching("ollama serve"):
        try:
            os.kill(pid, signal.SIGTERM)
        except (ProcessLookupError, PermissionError):
            pass
    _fuser_kill_11434()
    for _ in range(20):
        if not is_up(cfg, timeout=1.0):
            return True
        time.sleep(0.25)
    for pid in _pids_matching("ollama serve"):
        try:
            os.kill(pid, signal.SIGKILL)
        except (ProcessLookupError, PermissionError):
            pass
    return not is_up(cfg, timeout=1.0)


def stop_serve(cfg: Config) -> bool:
    """Stop only ollama serve / :11434. Do not idle-stop origin or council."""
    for pid in _pids_matching("ollama serve"):
        try:
            os.kill(pid, signal.SIGTERM)
        except (ProcessLookupError, PermissionError):
            pass
    _fuser_kill_11434()
    for _ in range(20):
        if not is_up(cfg, timeout=1.0):
            return True
        time.sleep(0.25)
    for pid in _pids_matching("ollama serve"):
        try:
            os.kill(pid, signal.SIGKILL)
        except (ProcessLookupError, PermissionError):
            pass
    return not is_up(cfg, timeout=1.0)


def _serve_env() -> dict[str, str]:
    env = os.environ.copy()
    env.setdefault("OLLAMA_HOST", "127.0.0.1:11434")
    env["OLLAMA_KEEP_ALIVE"] = env.get("OLLAMA_KEEP_ALIVE") or "15m"
    env["OLLAMA_MAX_LOADED_MODELS"] = env.get("OLLAMA_MAX_LOADED_MODELS") or "1"
    env["OLLAMA_NUM_PARALLEL"] = env.get("OLLAMA_NUM_PARALLEL") or "1"
    env["AVA_OLLAMA_CHAT_KEEP_ALIVE"] = env.get("AVA_OLLAMA_CHAT_KEEP_ALIVE") or "15m"
    return env


def _npu_chat() -> bool:
    return npu_chat_enabled()


def warm_default(cfg: Config, timeout: float = 120) -> bool:
    """Map llama3.2 GGUF. Skip when NPU chat is on — GGUF plus FastFlowLM OOMs this 16 GB box."""
    if npu_chat_enabled() or flm_is_up():
        return True
    model = (os.getenv("AVA_OLLAMA_MODEL") or "llama3.2:3b-instruct-q4_K_M").strip()
    keep = (os.getenv("AVA_OLLAMA_CHAT_KEEP_ALIVE") or "15m").strip() or "15m"
    payload = json.dumps({"model": model, "keep_alive": keep}).encode("utf-8")
    req = urllib.request.Request(
        cfg.ollama_base.rstrip("/") + "/api/generate",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout):
            return True
    except Exception:
        return False


def start(cfg: Config) -> bool:
    """Ollama is owned by AVA Console. Do not start it while the terminal is closed."""
    if is_up(cfg):
        return True
    if not console_is_up():
        return False
    return is_up(cfg)
