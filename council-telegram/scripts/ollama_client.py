"""Ollama HTTP chat (localhost). Never uses coder models for council chat."""
from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
from typing import Any

from .config import CHAT_TIMEOUT_S, Config

UNLOAD_WAIT_S = 20.0


def _url(cfg: Config, path: str) -> str:
    return cfg.ollama_base.rstrip("/") + path


def _http(
    cfg: Config,
    path: str,
    payload: dict[str, Any] | None,
    timeout: float,
    *,
    method: str = "POST",
) -> tuple[dict[str, Any] | None, str]:
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Content-Type": "application/json"} if data is not None else {}
    req = urllib.request.Request(
        _url(cfg, path),
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as e:
        return None, f"[ollama unreachable: {e}]"
    except Exception as e:  # noqa: BLE001
        return None, f"[ollama error: {e}]"
    if not raw.strip():
        return {}, ""
    try:
        body = json.loads(raw)
    except json.JSONDecodeError:
        return {}, ""
    return body if isinstance(body, dict) else {}, ""


def _same_model(a: str, b: str) -> bool:
    return (a or "").strip().lower() == (b or "").strip().lower()


def loaded_names(cfg: Config) -> list[str]:
    body, err = _http(cfg, "/api/ps", None, 3, method="GET")
    if err or not body:
        return []
    rows = body.get("models") or []
    names: list[str] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        name = str(row.get("model") or row.get("name") or "").strip()
        if name:
            names.append(name)
    return names


def unload(cfg: Config, model: str, timeout: float = 30) -> None:
    name = (model or "").strip()
    if not name:
        return
    _http(cfg, "/api/generate", {"model": name, "keep_alive": 0}, timeout)


def unload_all(cfg: Config) -> None:
    """Drop every resident GGUF so the 840M stays cold while FastFlowLM is speaking."""
    for name in loaded_names(cfg):
        unload(cfg, name)


def ensure_solo(cfg: Config, model: str) -> None:
    """Unload every resident GGUF that is not `model` before we map a new one."""
    want = (model or "").strip()
    if not want:
        return
    for name in loaded_names(cfg):
        if _same_model(name, want):
            continue
        print(f"ollama unload {name} before {want}", flush=True)
        unload(cfg, name)
    deadline = time.time() + UNLOAD_WAIT_S
    while time.time() < deadline:
        leftover = [n for n in loaded_names(cfg) if not _same_model(n, want)]
        if not leftover:
            return
        time.sleep(0.25)
    leftover = [n for n in loaded_names(cfg) if not _same_model(n, want)]
    if leftover:
        print(f"ollama unload still resident {leftover} want={want}", flush=True)


def begin_turn(cfg: Config, model: str, *, voice: str = "") -> None:
    """Every council voice: drop other GGUFs, then map this one."""
    who = (voice or "").strip() or "model"
    print(f"voice-swap {who} solo={model}", flush=True)
    ensure_solo(cfg, model)


def _once(
    cfg: Config,
    model: str,
    messages: list[dict[str, str]],
    num_predict: int,
    timeout: float,
    *,
    keep_alive: int | str = "15m",
) -> tuple[str, str]:
    payload: dict[str, Any] = {
        "model": model,
        "stream": False,
        "keep_alive": keep_alive,
        "messages": messages,
        "options": {"num_predict": int(num_predict)},
    }
    body, err = _http(cfg, "/api/chat", payload, timeout)
    if err:
        return err, "error"
    if not body:
        return "[ollama error: empty]", "error"
    msg = body.get("message") or {}
    content = msg.get("content")
    text = content.strip() if isinstance(content, str) else str(body.get("response") or "").strip()
    reason = str(body.get("done_reason") or body.get("doneReason") or "").lower()
    return text, reason


def _npu_chat_env() -> bool:
    return os.getenv("AVA_NPU_CHAT", "1").strip().lower() not in {"0", "false", "off", "no"}


def _force_ollama_model(model: str) -> bool:
    """Dolphin / heat GGUFs must not be rewritten through FastFlowLM instruct."""
    m = (model or "").lower()
    return "dolphin" in m or "nchapman/" in m


def _flm_try(messages: list[dict[str, str]], timeout: float = 2.0, num_predict: int = 180) -> str | None:
    """OpenAI-compat FastFlowLM. None if the NPU server is down."""
    url = (os.getenv("AVA_FLM_URL") or "http://127.0.0.1:52625").rstrip("/") + "/v1/chat/completions"
    model = (os.getenv("AVA_FLM_MODEL") or "llama3.2:3b").strip()
    payload = {
        "model": model,
        "messages": messages,
        "stream": False,
        "max_tokens": max(64, min(int(num_predict or 180), 512)),
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
    except Exception:
        return None
    try:
        body = json.loads(raw)
    except json.JSONDecodeError:
        return None
    choices = body.get("choices") or []
    if not choices:
        return None
    msg = (choices[0] or {}).get("message") or {}
    text = msg.get("content")
    if isinstance(text, str) and text.strip():
        return text.strip()
    return None


def _ensure_flm() -> None:
    """Map NPU for council speak. Skip under pytest. Idle-stop still unmaps at 15m."""
    if not _npu_chat_env():
        return
    if os.getenv("PYTEST_CURRENT_TEST"):
        return
    from . import ollama_ctl

    if ollama_ctl.flm_is_up():
        return
    ollama_ctl.flm_start()
    # First chat after map needs the NPU ready — do not npu-miss into silence.
    deadline = time.monotonic() + 45.0
    while time.monotonic() < deadline:
        if ollama_ctl.flm_is_up():
            return
        time.sleep(1.0)


def chat(
    cfg: Config,
    model: str,
    system: str,
    user: str,
    num_predict: int = 180,
    timeout: float = CHAT_TIMEOUT_S,
    *,
    voice: str = "",
) -> str:
    messages = [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]
    if _npu_chat_env() and "coder" not in (model or "").lower() and not _force_ollama_model(model):
        unload_all(cfg)
        _ensure_flm()
        wait = min(float(timeout), 90.0)
        npu = _flm_try(messages, timeout=wait, num_predict=num_predict)
        if not npu:
            # One retry after a short settle — cold NPU map used to glitch the whole team.
            time.sleep(2.0)
            _ensure_flm()
            npu = _flm_try(messages, timeout=wait, num_predict=num_predict)
        if npu:
            return npu
        print("npu miss — not mapping GGUF chat", flush=True)
        return ""
    begin_turn(cfg, model, voice=voice)
    keep = (os.getenv("AVA_OLLAMA_CHAT_KEEP_ALIVE") or os.getenv("OLLAMA_KEEP_ALIVE") or "15m").strip()
    if keep in {"0", "0s", "0m"}:
        keep = "15m"
    try:
        # Stay mapped across continue retries. Unload only when keep_one_loaded is off.
        text, reason = _once(cfg, model, messages, num_predict, timeout, keep_alive=keep)
        if not text or text.startswith("[ollama"):
            return text
        pieces = [text]
        for _ in range(2):
            if reason != "length":
                break
            follow = messages + [
                {"role": "assistant", "content": "".join(pieces)},
                {
                    "role": "user",
                    "content": "Continue from exactly where you stopped. Finish the sentence. Do not repeat.",
                },
            ]
            extra, reason = _once(
                cfg, model, follow, min(int(num_predict), 400), timeout, keep_alive=keep
            )
            if not extra or extra.startswith("[ollama"):
                break
            joiner = "" if pieces[-1].endswith((" ", "\n")) or extra.startswith((" ", "\n")) else " "
            pieces.append(joiner + extra)
        return "".join(pieces).strip()
    finally:
        if not getattr(cfg, "keep_one_loaded", True):
            unload(cfg, model)
