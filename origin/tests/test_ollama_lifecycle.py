import time

from apps.core.services import ollama_lifecycle as life


def _no_flm(monkeypatch):
    monkeypatch.setattr("apps.council.ollama_ctl.flm_is_up", lambda: False)
    monkeypatch.setattr(
        "apps.council.ollama_ctl.flm_stop",
        lambda: (_ for _ in ()).throw(AssertionError("flm")),
    )


def test_idle_does_not_stop_when_recent(monkeypatch, tmp_path):
    monkeypatch.setattr(life, "STATE_PATH", tmp_path / "life.json")
    monkeypatch.setattr(life, "play_clip", lambda kind: True)
    monkeypatch.setattr(life, "_up", lambda: True)
    monkeypatch.setattr(life, "_busy", lambda: False)
    monkeypatch.setattr("apps.council.ollama_ctl.is_up", lambda cfg: True)
    monkeypatch.setattr(
        "apps.council.ollama_ctl.stop_serve",
        lambda cfg: (_ for _ in ()).throw(AssertionError("stop")),
    )
    _no_flm(monkeypatch)
    monkeypatch.setattr("apps.council.config.load_config", lambda: object())
    monkeypatch.setattr("apps.council.power_status.announce", lambda kind: {"ok": True, "skipped": True})
    life.touch(source="test")
    out = life.tick_idle()
    assert out["stopped"] is False


def test_idle_stops_after_fifteen_minutes(monkeypatch, tmp_path):
    monkeypatch.setattr(life, "STATE_PATH", tmp_path / "life.json")
    played = []
    monkeypatch.setattr(life, "play_clip", lambda kind: played.append(kind) or True)
    monkeypatch.setattr(life, "_busy", lambda: False)
    monkeypatch.setattr("apps.council.ollama_ctl.is_up", lambda cfg: True)
    monkeypatch.setattr("apps.council.ollama_ctl.flm_is_up", lambda: False)
    monkeypatch.setattr("apps.council.ollama_ctl.stop_serve", lambda cfg: True)
    monkeypatch.setattr("apps.council.ollama_ctl.flm_stop", lambda: False)
    monkeypatch.setattr("apps.council.config.load_config", lambda: object())
    monkeypatch.setattr("apps.council.power_status.announce", lambda kind: {"ok": True})
    monkeypatch.setattr(life, "_chat_activity_ts", lambda: 0)
    life.touch(source="test")
    row = life._load()
    row["last_at"] = int(time.time()) - life.IDLE_S - 5
    life._save(row)
    out = life.tick_idle()
    assert life.IDLE_S == 15 * 60
    assert out["stopped"] is True
    assert "down" in played


def test_idle_keeps_flm_mapped_when_ollama_already_down(monkeypatch, tmp_path):
    monkeypatch.setattr(life, "STATE_PATH", tmp_path / "life.json")
    monkeypatch.setattr(life, "play_clip", lambda kind: True)
    monkeypatch.setattr(life, "_busy", lambda: False)
    monkeypatch.setattr("apps.council.ollama_ctl.is_up", lambda cfg: False)
    monkeypatch.setattr("apps.council.ollama_ctl.flm_is_up", lambda: True)
    monkeypatch.setattr(
        "apps.council.ollama_ctl.stop_serve",
        lambda cfg: (_ for _ in ()).throw(AssertionError("ollama")),
    )
    monkeypatch.setattr(
        "apps.council.ollama_ctl.flm_stop",
        lambda: (_ for _ in ()).throw(AssertionError("flm")),
    )
    monkeypatch.setattr("apps.council.config.load_config", lambda: object())
    monkeypatch.setattr("apps.council.power_status.announce", lambda kind: {"ok": True})
    monkeypatch.setattr(life, "_chat_activity_ts", lambda: 0)
    life.touch(source="test")
    row = life._load()
    row["last_at"] = int(time.time()) - life.IDLE_S - 5
    life._save(row)
    out = life.tick_idle()
    assert out["stopped"] is False
    assert out["reason"] == "npu_session"


def test_message_starts_npu_not_gguf(monkeypatch, tmp_path):
    monkeypatch.setattr(life, "STATE_PATH", tmp_path / "life.json")
    played = []
    monkeypatch.setattr(life, "play_clip", lambda kind: played.append(kind) or True)
    monkeypatch.setattr("apps.council.ollama_ctl.is_up", lambda cfg: False)
    monkeypatch.setattr("apps.council.ollama_ctl.flm_is_up", lambda: True)
    monkeypatch.setattr(
        "apps.council.ollama_ctl.start",
        lambda cfg: (_ for _ in ()).throw(AssertionError("gguf")),
    )
    monkeypatch.setattr("apps.council.config.load_config", lambda: object())
    monkeypatch.setattr("apps.council.power_status.announce", lambda kind: {"ok": True})
    out = life.on_message(source="telegram")
    assert out["started"] is True
    assert out["engine"] == "npu"
    assert played == ["loading"]


def test_idle_stays_up_if_council_just_spoke(monkeypatch, tmp_path):
    monkeypatch.setattr(life, "STATE_PATH", tmp_path / "life.json")
    monkeypatch.setattr(life, "play_clip", lambda kind: True)
    monkeypatch.setattr(life, "_busy", lambda: False)
    monkeypatch.setattr("apps.council.ollama_ctl.is_up", lambda cfg: True)
    _no_flm(monkeypatch)
    monkeypatch.setattr(
        "apps.council.ollama_ctl.stop_serve",
        lambda cfg: (_ for _ in ()).throw(AssertionError("stop")),
    )
    monkeypatch.setattr("apps.council.config.load_config", lambda: object())
    now = int(time.time())
    monkeypatch.setattr(life, "_chat_activity_ts", lambda: now)
    life.touch(source="old")
    row = life._load()
    row["last_at"] = now - life.IDLE_S - 30
    life._save(row)
    out = life.tick_idle()
    assert out["stopped"] is False
    assert out["reason"] == "active"


def test_idle_stays_up_during_brainstorm(monkeypatch, tmp_path):
    monkeypatch.setattr(life, "STATE_PATH", tmp_path / "life.json")
    monkeypatch.setattr(life, "play_clip", lambda kind: True)
    monkeypatch.setattr(life, "_busy", lambda: True)
    monkeypatch.setattr("apps.council.ollama_ctl.is_up", lambda cfg: True)
    monkeypatch.setattr("apps.council.ollama_ctl.flm_is_up", lambda: True)
    monkeypatch.setattr(
        "apps.council.ollama_ctl.stop_serve",
        lambda cfg: (_ for _ in ()).throw(AssertionError("stop")),
    )
    monkeypatch.setattr(
        "apps.council.ollama_ctl.flm_stop",
        lambda: (_ for _ in ()).throw(AssertionError("flm")),
    )
    monkeypatch.setattr("apps.council.config.load_config", lambda: object())
    monkeypatch.setattr(life, "_chat_activity_ts", lambda: 0)
    life.touch(source="test")
    row = life._load()
    row["last_at"] = int(time.time()) - life.IDLE_S - 5
    life._save(row)
    out = life.tick_idle()
    assert out["stopped"] is False
    assert out["reason"] == "busy"
