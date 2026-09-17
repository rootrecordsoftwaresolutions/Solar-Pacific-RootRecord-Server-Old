from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient

from apps.core.main import app
from apps.core.services import feature_toggles, obs_presence


client = TestClient(app)


def test_ops_features_roundtrip(tmp_path: Path, monkeypatch):
    from apps.core import config

    monkeypatch.setattr(config, "DATA_DIR", tmp_path)
    monkeypatch.setattr(config, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(obs_presence, "obs_process_running", lambda force=False: True)

    got = client.get("/api/ops/features")
    assert got.status_code == 200, got.text
    body = got.json()
    assert body.get("ok") is True
    assert body.get("obs_launches_process") is False
    flags = body.get("flags") or {}
    assert flags.get("obs") is False
    assert flags.get("morning_report") is False

    patched = client.post("/api/ops/features", json={"flags": {"obs": True, "morning_report": True}})
    assert patched.status_code == 200, patched.text
    next_flags = (patched.json().get("flags") or {})
    assert next_flags.get("obs") is True
    assert next_flags.get("morning_report") is True
    saved = (tmp_path / "state" / "feature-toggles.json")
    assert saved.is_file()


def test_obs_work_allowed_false_when_night(tmp_path: Path, monkeypatch):
    from apps.core import config

    monkeypatch.setattr(config, "DATA_DIR", tmp_path)
    monkeypatch.setattr(config, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(obs_presence, "obs_process_running", lambda force=False: True)
    monkeypatch.setattr(obs_presence, "_night_sleeping", lambda: True)
    feature_toggles.save({**feature_toggles.load(), "obs": True})
    assert obs_presence.obs_work_allowed() is False


def test_obs_work_allowed_stays_off_when_process_mocked(tmp_path: Path, monkeypatch):
    from apps.core import config

    monkeypatch.setattr(config, "DATA_DIR", tmp_path)
    monkeypatch.setattr(config, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(obs_presence, "obs_process_running", lambda force=False: True)
    monkeypatch.setattr(obs_presence, "_night_sleeping", lambda: False)
    feature_toggles.save({**feature_toggles.load(), "obs": False})
    assert obs_presence.obs_work_allowed() is False
    feature_toggles.save({**feature_toggles.load(), "obs": True})
    assert obs_presence.obs_work_allowed() is True


def test_obs_rotator_not_scheduled_until_toggle():
    from apps.core.scheduler import Scheduler

    sched = Scheduler()
    assert sched._apscheduler.get_job("broadcast-loop") is None
    assert sched._apscheduler.get_job("kilauea-cams") is None
    assert sched._apscheduler.get_job("hurricane-obs") is None
    on = sched.set_obs_jobs(True)
    assert on.get("enabled") is True
    assert sched._apscheduler.get_job("broadcast-loop") is not None
    assert sched._apscheduler.get_job("kilauea-cams") is not None
    assert sched._apscheduler.get_job("hurricane-obs") is not None
    sched.set_obs_jobs(False)
    assert sched._apscheduler.get_job("broadcast-loop") is None
    assert sched._apscheduler.get_job("kilauea-cams") is None
    assert sched._apscheduler.get_job("hurricane-obs") is None


def test_obs_defaults_off_even_if_env_enable(tmp_path: Path, monkeypatch):
    from apps.core import config

    monkeypatch.setattr(config, "DATA_DIR", tmp_path)
    monkeypatch.setattr(config, "STATE_DIR", tmp_path / "state")
    monkeypatch.setattr(config, "ENABLE_OBS", True)
    flags = feature_toggles.load()
    assert flags.get("obs") is False


def test_obs_auto_switch_defaults_off(tmp_path: Path, monkeypatch):
    from apps.core import config
    from apps.voice import director

    monkeypatch.setattr(config, "DATA_DIR", tmp_path)
    monkeypatch.delenv("AVA_OBS_AUTO_SWITCH", raising=False)
    assert director.obs_auto_switch_enabled() is False
