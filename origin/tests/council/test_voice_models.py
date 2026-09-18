from apps.council.config import (
    BRUCE_MODEL_DEFAULT,
    CARLY_MODEL_DEFAULT,
    CODER_MODELS_BLOCKED,
    Config,
    DEFAULT_CHAT_MODEL,
)


def test_general_queries_stay_on_fast_chat_model():
    cfg = Config(
        keep_one_loaded=True,
        chat_model=DEFAULT_CHAT_MODEL,
        ava_model="qwen2.5:7b-instruct-q4_K_M",
        bruce_model=BRUCE_MODEL_DEFAULT,
        carly_model=CARLY_MODEL_DEFAULT,
    )
    assert "3b" in DEFAULT_CHAT_MODEL
    assert cfg.model_for("ava", full_thought=False) == DEFAULT_CHAT_MODEL
    assert cfg.model_for("bruce", full_thought=False) == DEFAULT_CHAT_MODEL
    assert cfg.model_for("carly", full_thought=False) == DEFAULT_CHAT_MODEL
    # DMs stay on the same NPU chat model (heat/trust is prompt-only).
    assert cfg.model_for("ava", dm=True, full_thought=False) == DEFAULT_CHAT_MODEL
    assert cfg.model_for("bruce", dm=True, full_thought=False) == DEFAULT_CHAT_MODEL


def test_brainstorm_stays_on_fast_chat_model():
    cfg = Config(
        keep_one_loaded=True,
        chat_model=DEFAULT_CHAT_MODEL,
        bruce_model=BRUCE_MODEL_DEFAULT,
        carly_model=CARLY_MODEL_DEFAULT,
    )
    assert cfg.model_for("ava", full_thought=True) == DEFAULT_CHAT_MODEL
    assert cfg.model_for("bruce", full_thought=True) == DEFAULT_CHAT_MODEL
    assert cfg.model_for("carly", full_thought=True) == DEFAULT_CHAT_MODEL
    assert "8b" not in cfg.model_for("bruce", full_thought=True)
    for blocked in CODER_MODELS_BLOCKED:
        assert blocked not in cfg.model_for("carly", full_thought=True)


def test_coder_override_falls_back():
    cfg = Config(keep_one_loaded=False, chat_model=DEFAULT_CHAT_MODEL, carly_model="qwen2.5-coder:7b")
    assert cfg.model_for("carly", full_thought=True) == DEFAULT_CHAT_MODEL
