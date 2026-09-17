import time

from apps.council import self_repair


def test_self_repair_window_and_hour_cap(monkeypatch, tmp_path):
    monkeypatch.setattr(self_repair, "PATH", tmp_path / "self-repair.json")
    monkeypatch.setattr("apps.council.state.load_state", lambda *a, **k: {})
    monkeypatch.setattr("apps.council.state.save_state", lambda *a, **k: None)
    snap = self_repair.enable(minutes=30, max_per_hour=30)
    assert snap["active"] is True
    assert snap["max_per_hour"] == 30
    assert snap["remaining_s"] > 29 * 60
    ok, _ = self_repair.can_launch()
    assert ok is True
    data = self_repair._load()
    now = int(time.time())
    data["launches"] = [now] * 30
    data["pid"] = 0
    self_repair._save(data)
    ok, reason = self_repair.can_launch()
    assert ok is False
    assert "30/hour" in reason
    self_repair.disable()
    assert self_repair.active() is False


def test_self_repair_prompt_block_empty_when_off(monkeypatch, tmp_path):
    monkeypatch.setattr(self_repair, "PATH", tmp_path / "self-repair.json")
    monkeypatch.setattr("apps.council.state.load_state", lambda *a, **k: {})
    monkeypatch.setattr("apps.council.state.save_state", lambda *a, **k: None)
    self_repair.disable()
    assert self_repair.prompt_block() == ""
    self_repair.enable(minutes=30)
    block = self_repair.prompt_block()
    assert "SELF-REPAIR WINDOW" in block
    assert "30" in block
