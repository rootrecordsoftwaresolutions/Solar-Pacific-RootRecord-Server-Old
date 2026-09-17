from __future__ import annotations

from datetime import datetime
from pathlib import Path

from apps.core import host_metrics as hm


def test_linux_npu_present_accel(tmp_path, monkeypatch):
    accel = tmp_path / "accel0"
    accel.write_bytes(b"")
    monkeypatch.setattr(hm, "_LINUX_ACCEL", accel)
    monkeypatch.setattr(hm, "_LINUX_AMDXDNA", tmp_path / "missing-driver")
    assert hm._linux_npu_present() is True


def test_linux_npu_absent(tmp_path, monkeypatch):
    monkeypatch.setattr(hm, "_LINUX_ACCEL", tmp_path / "no-accel")
    monkeypatch.setattr(hm, "_LINUX_AMDXDNA", tmp_path / "no-driver")
    assert hm._linux_npu_present() is False


def test_linux_gpu_pct_from_sysfs(tmp_path, monkeypatch):
    drm = tmp_path / "drm"
    card = drm / "card1"
    (card / "device").mkdir(parents=True)
    (card / "device" / "gpu_busy_percent").write_text("7\n", encoding="utf-8")
    (drm / "card1-eDP-1").mkdir()
    monkeypatch.setattr(hm, "_LINUX_DRM", drm)
    assert hm._linux_gpu_pct() == 7.0


def test_linux_npu_pct_idle_without_client(tmp_path, monkeypatch):
    accel = tmp_path / "accel0"
    accel.write_bytes(b"")
    monkeypatch.setattr(hm, "_LINUX_ACCEL", accel)
    monkeypatch.setattr(hm, "_LINUX_AMDXDNA", tmp_path / "missing-driver")
    proc = tmp_path / "proc"
    proc.mkdir()
    assert hm._linux_npu_pct(wait_s=0.05, proc_root=proc) == 0.0


def test_linux_npu_fdinfo_busy_parses_amdxdna(tmp_path):
    proc = tmp_path / "1234"
    fdinfo = proc / "fdinfo"
    fdinfo.mkdir(parents=True)
    (fdinfo / "5").write_text(
        "drm-driver:\tamdxdna\n"
        "drm-client-id:\t9\n"
        "drm-engine-npu:\t250000000\n",
        encoding="utf-8",
    )
    (fdinfo / "6").write_text(
        "drm-driver:\tamdgpu\n"
        "drm-engine-gfx:\t999\n",
        encoding="utf-8",
    )
    busy, found = hm._linux_npu_fdinfo_busy(tmp_path)
    assert found is True
    assert busy == {"9:npu": 250000000}


def test_net_usage_window_needs_coverage():
    now = {"at": 3_600_000, "iface": "wlo1", "rx": 2000, "tx": 500, "link": "wireless"}
    rows = [{"at": 3_000_000, "iface": "wlo1", "rx": 1000, "tx": 100}]
    hour = hm.net_usage_window(3600, now=now, rows=rows, min_cover=0.75)
    assert hour is None
    rows = [{"at": 100_000, "iface": "wlo1", "rx": 1000, "tx": 100}]
    hour = hm.net_usage_window(3600, now=now, rows=rows, min_cover=0.75)
    assert hour is not None
    assert hour["rx"] == 1000
    assert hour["tx"] == 400
    assert hour["total"] == 1400


def test_spoken_bytes():
    assert "bytes" in hm.spoken_bytes(20)
    assert "kilobytes" in hm.spoken_bytes(2048)
    assert "megabytes" in hm.spoken_bytes(2_000_000)


def test_auth_fail_counts_windows(tmp_path, monkeypatch):
    log = tmp_path / "auth.log"
    log.write_text(
        "2026-09-17T00:10:00-10:00 host sshd: Failed password for x from 1.2.3.4\n"
        "2026-09-16T12:00:00-10:00 host sshd: Failed password for y from 1.2.3.4\n"
        "2026-09-17T00:11:00-10:00 host CRON: session opened\n",
        encoding="utf-8",
    )
    monkeypatch.setattr(hm, "_AUTH_LOGS", (log, tmp_path / "missing"))
    now = datetime.fromisoformat("2026-09-17T00:40:00-10:00").timestamp()
    got = hm._auth_fail_counts(now)
    assert got["failed_1h"] == 1
    assert got["failed_24h"] == 2
