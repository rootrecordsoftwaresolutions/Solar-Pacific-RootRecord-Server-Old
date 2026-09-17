from apps.council.ollama_client import _same_model, chat, ensure_solo


class _FakeCfg:
    ollama_base = "http://127.0.0.1:9"
    keep_one_loaded = True


def _quiet_ram(monkeypatch):
    monkeypatch.setattr("apps.council.ollama_client.ensure_solo", lambda *_a, **_k: None)
    monkeypatch.setattr("apps.council.ollama_client.unload", lambda *_a, **_k: None)
    monkeypatch.setattr("apps.council.ollama_client._flm_try", lambda *_a, **_k: None)
    monkeypatch.setattr("apps.council.ollama_client._npu_chat_env", lambda: False)


def test_chat_continues_on_length(monkeypatch):
    _quiet_ram(monkeypatch)
    calls = {"n": 0}

    def fake_once(cfg, model, messages, num_predict, timeout, **_k):
        calls["n"] += 1
        if calls["n"] == 1:
            return "Root Record is currently in a phase where we're closely monitoring the transition to", "length"
        return "ensure it aligns with our goals.", "stop"

    monkeypatch.setattr("apps.council.ollama_client._once", fake_once)
    out = chat(_FakeCfg(), "llama3.2", "sys", "hi", num_predict=80)
    assert "aligns with our goals" in out
    assert out.startswith("Root Record")
    assert calls["n"] == 2


def test_chat_continues_twice_if_still_length(monkeypatch):
    _quiet_ram(monkeypatch)
    calls = {"n": 0}

    def fake_once(cfg, model, messages, num_predict, timeout, **_k):
        calls["n"] += 1
        if calls["n"] == 1:
            return "part one", "length"
        if calls["n"] == 2:
            return "part two", "length"
        return "part three.", "stop"

    monkeypatch.setattr("apps.council.ollama_client._once", fake_once)
    out = chat(_FakeCfg(), "llama3.2", "sys", "hi", num_predict=80)
    assert out == "part one part two part three."
    assert calls["n"] == 3


def test_ensure_solo_unloads_other_ggufs(monkeypatch):
    state = {"loaded": ["qwen2.5:7b-instruct-q4_K_M", "llama3.1:8b-instruct-q4_K_M"]}
    dropped: list[str] = []

    monkeypatch.setattr(
        "apps.council.ollama_client.loaded_names",
        lambda _cfg: list(state["loaded"]),
    )

    def fake_unload(_cfg, name, timeout=30):
        dropped.append(name)
        state["loaded"] = [n for n in state["loaded"] if n != name]

    monkeypatch.setattr("apps.council.ollama_client.unload", fake_unload)
    monkeypatch.setattr("apps.council.ollama_client.UNLOAD_WAIT_S", 1.0)
    ensure_solo(_FakeCfg(), "llama3.1:8b-instruct-q4_K_M")
    assert dropped == ["qwen2.5:7b-instruct-q4_K_M"]
    assert _same_model("llama3.1:8b-instruct-q4_K_M", "llama3.1:8b-instruct-q4_K_M")


def test_chat_keeps_model_when_keep_one_loaded(monkeypatch):
    _quiet_ram(monkeypatch)
    dropped: list[str] = []
    monkeypatch.setattr("apps.council.ollama_client.ensure_solo", lambda *_a, **_k: None)
    monkeypatch.setattr(
        "apps.council.ollama_client.unload",
        lambda _cfg, name, timeout=30: dropped.append(name),
    )
    monkeypatch.setattr(
        "apps.council.ollama_client._once",
        lambda *_a, **_k: ("ok", "stop"),
    )
    out = chat(_FakeCfg(), "llama3.2", "sys", "hi", num_predict=80)
    assert out == "ok"
    assert dropped == []


def test_chat_unloads_when_keep_one_off(monkeypatch):
    _quiet_ram(monkeypatch)
    dropped: list[str] = []
    monkeypatch.setattr("apps.council.ollama_client.ensure_solo", lambda *_a, **_k: None)
    monkeypatch.setattr(
        "apps.council.ollama_client.unload",
        lambda _cfg, name, timeout=30: dropped.append(name),
    )
    monkeypatch.setattr(
        "apps.council.ollama_client._once",
        lambda *_a, **_k: ("ok", "stop"),
    )
    cfg = _FakeCfg()
    cfg.keep_one_loaded = False
    out = chat(cfg, "llama3.2", "sys", "hi", num_predict=80)
    assert out == "ok"
    assert dropped == ["llama3.2"]


def test_warm_default_skips_gguf_when_npu_chat(monkeypatch):
    from apps.council.ollama_ctl import warm_default

    monkeypatch.setenv("AVA_NPU_CHAT", "1")
    monkeypatch.setattr("apps.council.ollama_ctl.flm_is_up", lambda: False)
    monkeypatch.setattr(
        "apps.council.ollama_ctl.urllib.request.urlopen",
        lambda *_a, **_k: (_ for _ in ()).throw(AssertionError("gguf")),
    )
    assert warm_default(_FakeCfg()) is True


def test_chat_does_not_fall_back_to_ollama_when_flm_misses(monkeypatch):
    monkeypatch.setattr("apps.council.ollama_client.ensure_solo", lambda *_a, **_k: None)
    monkeypatch.setattr("apps.council.ollama_client.unload", lambda *_a, **_k: None)
    monkeypatch.setattr("apps.council.ollama_client._npu_chat_env", lambda: True)
    monkeypatch.setattr("apps.council.ollama_client._flm_try", lambda *_a, **_k: None)
    monkeypatch.setattr(
        "apps.council.ollama_client._once",
        lambda *_a, **_k: (_ for _ in ()).throw(AssertionError("gguf")),
    )
    monkeypatch.setattr("apps.council.ollama_client.unload_all", lambda *_a, **_k: None)
    monkeypatch.setattr("apps.council.ollama_client._ensure_flm", lambda: None)
    assert chat(_FakeCfg(), "llama3.2", "sys", "hi", num_predict=80) == ""
