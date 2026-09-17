from apps.council.power_status import LINES, MIN_AUTO_S, announce, should_announce


def test_debounce_same_kind(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.power_status.PATH", tmp_path / "power.json")
    monkeypatch.setattr("apps.council.power_status.CONFIG_DIR", tmp_path)
    posted: list[tuple[str, str]] = []
    announce("down", force=True, poster=lambda v, t: posted.append((v, t)) or {"ok": True})
    assert [v for v, _ in posted] == ["ava", "bruce", "carly"]
    assert "Powering down" in posted[0][1]
    posted.clear()
    out = announce("down", force=True, poster=lambda v, t: posted.append((v, t)) or {"ok": True})
    assert out["skipped"] is True
    assert posted == []
    assert should_announce("up", force=True) is True
    announce("up", force=True, poster=lambda v, t: posted.append((v, t)) or {"ok": True})
    assert posted[0][0] == "ava"
    assert "Coming online" in posted[0][1]
    assert LINES["up"][1][0] == "bruce"


def test_auto_idle_wake_not_every_flap(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.power_status.PATH", tmp_path / "power.json")
    monkeypatch.setattr("apps.council.power_status.CONFIG_DIR", tmp_path)
    posted: list[tuple[str, str]] = []
    announce("down", poster=lambda v, t: posted.append((v, t)) or {"ok": True})
    assert posted
    posted.clear()
    out = announce("up", poster=lambda v, t: posted.append((v, t)) or {"ok": True})
    assert out["skipped"] is True
    assert posted == []
    assert should_announce("up") is False
    assert MIN_AUTO_S >= 15 * 60
    assert should_announce("up", force=True) is True

