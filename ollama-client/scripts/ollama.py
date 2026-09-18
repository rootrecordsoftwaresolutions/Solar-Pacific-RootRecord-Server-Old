"""Ollama local brain client (Qwen3 / ava-ivy model)."""

from __future__ import annotations

import asyncio
import logging
import os
import time
from pathlib import Path

import httpx

from .. import config

log = logging.getLogger("ava.ollama")


def _chat_keep_alive(explicit=None):
    if explicit is not None:
        return explicit
    raw = (os.getenv("AVA_OLLAMA_CHAT_KEEP_ALIVE") or "").strip()
    if raw:
        return raw
    server = (os.getenv("OLLAMA_KEEP_ALIVE") or "").strip()
    if server and server not in {"0", "0s", "0m"}:
        return server
    return "15m"


def _coder_or_vision(model: str | None) -> bool:
    want = (model or "").lower()
    return (
        "coder" in want
        or "moondream" in want
        or "llava" in want
        or "qwen2.5vl" in want
        or "qwen2.5-vl" in want
        or "qwen3-vl" in want
        or "qwen3vl" in want
        or "minicpm-v" in want
        or "vision" in want
    )


def use_npu_chat(model: str | None = None) -> bool:
    if not getattr(config, "NPU_CHAT", True):
        return False
    if _coder_or_vision(model):
        return False
    return True


def _flm_base() -> str:
    return (getattr(config, "FLM_URL", None) or os.getenv("AVA_FLM_URL") or "http://127.0.0.1:52625").rstrip("/")


def flm_up() -> bool:
    try:
        r = httpx.get(f"{_flm_base()}/v1/models", timeout=1.0)
        return r.status_code == 200
    except Exception:
        return False


def ensure_flm() -> bool:
    """Map FastFlowLM for agent chat. Skip under pytest. Login still does not start it."""
    if not use_npu_chat():
        return False
    if flm_up():
        return True
    if os.getenv("PYTEST_CURRENT_TEST"):
        return False
    try:
        from apps.council import ollama_ctl

        return bool(ollama_ctl.flm_start())
    except Exception:
        return flm_up()


def wait_flm(*, timeout_s: float | None = None) -> bool:
    """Wait until FastFlowLM answers. Does not start a second copy."""
    if not use_npu_chat():
        return False
    if flm_up():
        return True
    if timeout_s is None:
        timeout_s = float(os.getenv("AVA_FLM_WAIT_S") or "180")
    deadline = time.monotonic() + max(0.0, timeout_s)
    while time.monotonic() < deadline:
        time.sleep(1.0)
        if flm_up():
            return True
    return False


def _flm_model(model: str | None) -> str:
    tagged = (model or "").strip()
    if tagged.startswith("llama3.2") and ":" in tagged and "instruct" not in tagged:
        return tagged
    return (getattr(config, "FLM_MODEL", None) or "llama3.2:3b").strip()


def _flm_content(data: dict) -> str | None:
    choices = data.get("choices") or []
    if not choices:
        return None
    msg = (choices[0] or {}).get("message") or {}
    text = msg.get("content")
    return str(text) if text else None


def _flm_max_tokens(num_predict: int | None = None) -> int:
    raw = (os.getenv("AVA_FLM_MAX_TOKENS") or "512").strip()
    try:
        cap = max(64, min(int(raw), 2048))
    except ValueError:
        cap = 512
    if num_predict is not None:
        return max(64, min(int(num_predict), cap))
    return cap


def _flm_chat_sync(
    messages: list[dict], *, model: str | None, timeout: int, num_predict: int | None = None
) -> str | None:
    try:
        r = httpx.post(
            f"{_flm_base()}/v1/chat/completions",
            json={
                "model": _flm_model(model),
                "messages": messages,
                "stream": False,
                "max_tokens": _flm_max_tokens(num_predict),
            },
            timeout=timeout,
        )
        if r.status_code == 200:
            return _flm_content(r.json() or {})
        log.debug("FLM sync status %s", r.status_code)
        return None
    except Exception as e:
        log.debug("FLM sync unavailable: %s", e)
        return None


async def _flm_chat(messages: list[dict], *, model: str | None, timeout: int) -> str | None:
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            r = await client.post(
                f"{_flm_base()}/v1/chat/completions",
                json={
                    "model": _flm_model(model),
                    "messages": messages,
                    "stream": False,
                    "max_tokens": _flm_max_tokens(),
                },
            )
        if r.status_code == 200:
            return _flm_content(r.json() or {}) or ""
        log.debug("FLM status %s", r.status_code)
        return None
    except Exception as e:
        log.debug("FLM unavailable: %s", e)
        return None

# /api/activity is polled every 2s from the GUI; do not hit Ollama that often.
_TAGS_TTL_S = 30.0
_tags_cache: tuple[float, bool, list[str]] = (0.0, False, [])


def _payload(messages: list[dict], model: str | None, *, keep_alive=None, num_predict: int | None = None) -> dict:
    options: dict = {
        "num_ctx": int(config.OLLAMA_NUM_CTX),
    }
    if num_predict is not None:
        options["num_predict"] = int(num_predict)
    body = {
        "model": model or config.OLLAMA_MODEL,
        "messages": messages,
        "stream": False,
        "think": False,
        "options": options,
    }
    if keep_alive is not None:
        body["keep_alive"] = keep_alive
    return body


def _solo_before(model: str | None) -> None:
    """Same council pipe: unload other GGUFs before this reply maps."""
    want = (model or config.OLLAMA_MODEL or "").strip()
    if not want:
        return
    try:
        from apps.council.config import Config
        from apps.council.ollama_client import begin_turn

        begin_turn(Config(ollama_base=config.OLLAMA_URL), want, voice="origin")
    except Exception:
        log.debug("ollama solo skip", exc_info=True)


def chat_sync(
    messages: list[dict],
    *,
    model: str | None = None,
    timeout: int = 90,
    keep_alive=None,
    num_predict: int | None = None,
) -> str | None:
    """Blocking chat. Everyday speak is FastFlowLM on the NPU. No GGUF fallback."""
    if use_npu_chat(model):
        ensure_flm()
        if not flm_up():
            wait_flm(timeout_s=min(float(timeout), 90.0))
        got = _flm_chat_sync(messages, model=model, timeout=timeout, num_predict=num_predict)
        if got is not None:
            return got
        log.warning("npu miss — not mapping GGUF chat")
        return None
    _solo_before(model)
    try:
        r = httpx.post(
            f"{config.OLLAMA_URL}/api/chat",
            json=_payload(
                messages,
                model,
                keep_alive=_chat_keep_alive(keep_alive),
                num_predict=num_predict,
            ),
            timeout=timeout,
        )
        if r.status_code == 200:
            return (r.json().get("message") or {}).get("content") or None
        log.debug("Ollama sync status %s", r.status_code)
        return None
    except Exception as e:
        log.debug("Ollama sync unavailable: %s", e)
        return None


async def chat(
    messages: list[dict],
    *,
    model: str | None = None,
    timeout: int = 90,
    keep_alive=None,
) -> str | None:
    """Returns reply string or None if local LLM is unavailable."""
    if use_npu_chat(model):
        await asyncio.to_thread(ensure_flm)
        if not flm_up():
            await asyncio.to_thread(wait_flm, timeout_s=min(float(timeout), 90.0))
        got = await _flm_chat(messages, model=model, timeout=timeout)
        if got is not None:
            return got
        log.warning("npu miss — not mapping GGUF chat")
        return None
    await asyncio.to_thread(_solo_before, model)
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            r = await client.post(
                f"{config.OLLAMA_URL}/api/chat",
                json=_payload(messages, model, keep_alive=_chat_keep_alive(keep_alive)),
            )
        if r.status_code == 200:
            return r.json().get("message", {}).get("content", "")
        log.debug("Ollama status %s", r.status_code)
        return None
    except Exception as e:
        log.debug("Ollama unavailable: %s", e)
        return None


async def tags(force: bool = False) -> tuple[bool, list[str]]:
    """Cached GET /api/tags. Returns (up, model_names)."""
    global _tags_cache
    ts, up, models = _tags_cache
    if not force and (time.monotonic() - ts) < _TAGS_TTL_S:
        return up, models
    try:
        async with httpx.AsyncClient(timeout=2.0) as client:
            r = await client.get(f"{config.OLLAMA_URL}/api/tags")
        ok = r.status_code == 200
        names: list[str] = []
        if ok:
            for m in (r.json() or {}).get("models") or []:
                name = str(m.get("name") or "")
                if name:
                    names.append(name)
        if use_npu_chat() and flm_up():
            tag = str(getattr(config, "FLM_MODEL", None) or "llama3.2:3b")
            names = [tag] + [n for n in names if n != tag]
            ok = True
        _tags_cache = (time.monotonic(), ok, names)
        return ok, names
    except Exception as e:
        log.debug("Ollama tags unavailable: %s", e)
        if use_npu_chat() and flm_up():
            tag = str(getattr(config, "FLM_MODEL", None) or "llama3.2:3b")
            _tags_cache = (time.monotonic(), True, [tag])
            return True, [tag]
        _tags_cache = (time.monotonic(), False, [])
        return False, []


async def is_available() -> bool:
    up, _ = await tags()
    return up


def _b64_file(path: Path, *, max_bytes: int = 1_800_000) -> str | None:
    try:
        raw = path.read_bytes()
    except OSError:
        return None
    if not raw or len(raw) > max_bytes:
        return None
    import base64

    return base64.b64encode(raw).decode("ascii")


def look_sync(prompt: str, images: list[Path], *, timeout: int = 90) -> str | None:
    """One-shot vision. Unloads after the reply so llama3.2 can talk."""
    blobs = []
    for p in images[:2]:
        b = _b64_file(Path(p))
        if b:
            blobs.append(b)
    if not blobs:
        return None
    payload = {
        "model": config.OLLAMA_VISION_MODEL,
        "messages": [
            {
                "role": "user",
                "content": (prompt or "Describe this still in four short factual sentences.")[:800],
                "images": blobs,
            }
        ],
        "stream": False,
        "think": False,
        "keep_alive": 0,
        "options": {"num_ctx": 2048, "temperature": 0.1},
    }
    try:
        r = httpx.post(f"{config.OLLAMA_URL}/api/chat", json=payload, timeout=timeout)
        if r.status_code != 200:
            log.warning("Ollama look HTTP %s %s", r.status_code, (r.text or "")[:200])
            return None
        return (r.json().get("message") or {}).get("content") or None
    except Exception as e:
        log.warning("Ollama look failed: %s", e)
        return None


async def look(prompt: str, images: list, *, timeout: int = 90) -> str | None:
    import asyncio
    from pathlib import Path

    paths = [Path(p) for p in images]
    return await asyncio.to_thread(look_sync, prompt, paths, timeout=timeout)
