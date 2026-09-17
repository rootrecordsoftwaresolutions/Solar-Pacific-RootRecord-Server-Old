from __future__ import annotations

from apps.core.services import ollama as ollama_svc


def test_use_npu_chat_skips_coder(monkeypatch):
    monkeypatch.setattr(ollama_svc.config, "NPU_CHAT", True)
    assert ollama_svc.use_npu_chat("llama3.2:3b-instruct-q4_K_M") is True
    assert ollama_svc.use_npu_chat(None) is True
    assert ollama_svc.use_npu_chat("qwen2.5-coder:7b") is False
    assert ollama_svc.use_npu_chat("moondream:latest") is False


def test_wait_flm_ready(monkeypatch):
    monkeypatch.setattr(ollama_svc, "use_npu_chat", lambda model=None: True)
    monkeypatch.setattr(ollama_svc, "flm_up", lambda: True)
    monkeypatch.setattr(ollama_svc.time, "sleep", lambda s: (_ for _ in ()).throw(AssertionError("sleep")))
    assert ollama_svc.wait_flm(timeout_s=5) is True


def test_chat_sync_maps_npu_first(monkeypatch):
    monkeypatch.setattr(ollama_svc.config, "NPU_CHAT", True)
    monkeypatch.setattr(ollama_svc, "flm_up", lambda: False)
    monkeypatch.setattr(ollama_svc, "ensure_flm", lambda: True)
    monkeypatch.setattr(ollama_svc, "wait_flm", lambda **_k: True)
    monkeypatch.setattr(
        ollama_svc,
        "_flm_chat_sync",
        lambda *_a, **_k: "npu",
    )
    monkeypatch.setattr(
        ollama_svc,
        "_solo_before",
        lambda *_a, **_k: (_ for _ in ()).throw(AssertionError("gguf")),
    )
    assert ollama_svc.chat_sync([{"role": "user", "content": "hi"}]) == "npu"


def test_chat_sync_does_not_fall_back_to_gguf(monkeypatch):
    monkeypatch.setattr(ollama_svc.config, "NPU_CHAT", True)
    monkeypatch.setattr(ollama_svc, "flm_up", lambda: False)
    monkeypatch.setattr(ollama_svc, "ensure_flm", lambda: False)
    monkeypatch.setattr(ollama_svc, "wait_flm", lambda **_k: False)
    monkeypatch.setattr(ollama_svc, "_flm_chat_sync", lambda *_a, **_k: None)
    monkeypatch.setattr(
        ollama_svc,
        "_solo_before",
        lambda *_a, **_k: (_ for _ in ()).throw(AssertionError("gguf")),
    )
    assert ollama_svc.chat_sync([{"role": "user", "content": "hi"}]) is None
