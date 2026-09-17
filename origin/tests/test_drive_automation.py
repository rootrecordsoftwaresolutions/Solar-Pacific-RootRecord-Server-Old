from __future__ import annotations

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path


def _mod():
    p = (
        Path.home()
        / ".ollama"
        / "skills"
        / "ecoflow-river-car"
        / "scripts"
        / "drive_automation.py"
    )
    spec = spec_from_file_location("drive_automation", p)
    m = module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(m)
    return m


def test_tick_skips_when_auto_off(tmp_path, monkeypatch):
    m = _mod()
    monkeypatch.setattr(m, "state_path", lambda: tmp_path / "drive-automation.json")
    monkeypatch.setattr(m, "lock_path", lambda: tmp_path / "drive-automation.lock")
    monkeypatch.setattr(
        m,
        "session",
        lambda **kwargs: (_ for _ in ()).throw(AssertionError("session")),
    )
    out = m.tick(execute=True)
    assert out["skipped"] == "auto_off"


def test_tick_skips_copy_stub(tmp_path, monkeypatch):
    m = _mod()
    monkeypatch.setattr(m, "state_path", lambda: tmp_path / "drive-automation.json")
    monkeypatch.setattr(m, "lock_path", lambda: tmp_path / "drive-automation.lock")
    cfg = m.load_config()
    cfg["auto"] = True
    cfg["copy_jobs"] = [{"id": "archives", "enabled": True, "src": "/tmp/a", "dst": "/tmp/b"}]
    m.save_config(cfg)
    monkeypatch.setattr(
        m,
        "session",
        lambda **kwargs: (_ for _ in ()).throw(AssertionError("session")),
    )
    out = m.tick(execute=True)
    assert out["skipped"] == "copy_not_implemented"


def test_session_dry_run_does_not_copy(tmp_path, monkeypatch):
    m = _mod()
    monkeypatch.setattr(m, "state_path", lambda: tmp_path / "drive-automation.json")
    monkeypatch.setattr(m, "lock_path", lambda: tmp_path / "drive-automation.lock")
    monkeypatch.setattr(m, "power_on", lambda **kwargs: {"ok": True, "execute": kwargs.get("execute")})
    monkeypatch.setattr(m, "power_off", lambda **kwargs: (_ for _ in ()).throw(AssertionError("off")))
    out = m.session(execute=False)
    assert out["blocked"] == "dry_run"
    assert out["copy"] is None


def test_copy_jobs_empty_ok():
    m = _mod()
    assert m.run_copy_jobs([])["skipped"] == "no_copy_jobs"


def test_ops_drive_status():
    from fastapi.testclient import TestClient

    from apps.core.main import app

    client = TestClient(app)
    got = client.get("/api/ops/drive-automation")
    assert got.status_code == 200, got.text
    body = got.json()
    assert body.get("ok") is True
    assert body.get("backup_copy") is False
    assert body.get("auto") is False
