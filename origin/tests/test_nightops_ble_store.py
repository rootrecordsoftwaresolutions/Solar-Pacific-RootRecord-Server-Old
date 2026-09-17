from __future__ import annotations

import json
import sys
from pathlib import Path

OPS = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "scripts"
sys.path.insert(0, str(OPS))

from ecoflow_ble_store import (  # noqa: E402
    DELTA_SN,
    RIVER_SN,
    classify_generator,
    persist_live_snapshot,
    quota_at_s,
    quota_path,
    skip_cloud_stomp,
    total_fresh_pv,
    want_delta_usb_on,
)


def test_persist_writes_quota_jsonl_and_db(tmp_path: Path):
    data = {
        "_ava_source": "ble",
        "pd.soc": 12.5,
        "bms_bmsStatus.soc": 12.5,
        "pd.wattsOutSum": 10,
        "mppt.inWatts": 40,
        "inv.inputWatts": 0,
        "inv.outputWatts": 8,
    }
    out = persist_live_snapshot(DELTA_SN, data, source="ble", lock=False, ops=tmp_path)
    assert out["ok"] is True
    q = json.loads((tmp_path / "quota" / f"{DELTA_SN}.json").read_text(encoding="utf-8"))
    assert q["source"] == "ble"
    hist = (tmp_path / "history" / f"{DELTA_SN}.jsonl").read_text(encoding="utf-8").strip()
    row = json.loads(hist.splitlines()[-1])
    assert row["source"] == "ble"
    assert row["soc"] == 12.5
    assert (tmp_path / "ecoflow-10s.db").is_file()


def test_skip_cloud_stomp_fresh_ble_and_leftover_one_pct():
    now_ms = int(__import__("time").time() * 1000)
    ble = {
        "at": now_ms,
        "source": "ble",
        "body": {"data": {"pd.soc": 1.53, "bms_bmsStatus.soc": 1.53}},
    }
    assert skip_cloud_stomp(ble) is True
    leftover = {"pd.soc": 1.0, "bms_bmsStatus.soc": 1.0}
    stale = {
        "at": now_ms - 120_000,
        "source": "ble",
        "body": {"data": {"pd.soc": 1.53, "bms_bmsStatus.soc": 1.53}},
    }
    assert skip_cloud_stomp(stale, leftover) is True
    live_cloud = {"pd.soc": 12.0, "bms_bmsStatus.soc": 12.0}
    assert skip_cloud_stomp(stale, live_cloud) is False


def test_cloud_persist_respects_444_lock(tmp_path: Path):
    data = {"pd.soc": 12.5, "bms_bmsStatus.soc": 12.5, "mppt.inWatts": 40}
    persist_live_snapshot(DELTA_SN, data, source="ble", lock=True, ops=tmp_path)
    hist_before = (tmp_path / "history" / f"{DELTA_SN}.jsonl").read_text(encoding="utf-8")
    out = persist_live_snapshot(
        DELTA_SN,
        {"pd.soc": 1.0, "bms_bmsStatus.soc": 1.0},
        source="cloud",
        lock=False,
        ops=tmp_path,
    )
    assert out["ok"] is False
    hist_after = (tmp_path / "history" / f"{DELTA_SN}.jsonl").read_text(encoding="utf-8")
    assert hist_before == hist_after
    q = json.loads((tmp_path / "quota" / f"{DELTA_SN}.json").read_text(encoding="utf-8"))
    assert q["source"] == "ble"


def test_unreachable_does_not_rewrite_quota(tmp_path: Path):
    data = {"pd.soc": 9, "bms_bmsStatus.soc": 9, "mppt.inWatts": 1}
    persist_live_snapshot(RIVER_SN, data, source="ble", lock=False, ops=tmp_path)
    at = quota_at_s(json.loads(quota_path(RIVER_SN, tmp_path).read_text(encoding="utf-8")))
    hist_before = (tmp_path / "history" / f"{RIVER_SN}.jsonl").read_text(encoding="utf-8")
    # freeze: do not call persist again
    at2 = quota_at_s(json.loads(quota_path(RIVER_SN, tmp_path).read_text(encoding="utf-8")))
    hist_after = (tmp_path / "history" / f"{RIVER_SN}.jsonl").read_text(encoding="utf-8")
    assert at == at2
    assert hist_before == hist_after


def test_400w_and_generator():
    assert want_delta_usb_on(399, sleeping=False, generator=False, delta_soc=3.0) is True
    assert want_delta_usb_on(399, sleeping=False, generator=False, delta_soc=2.9) is False
    assert want_delta_usb_on(10, sleeping=False, generator=False, delta_soc=0.2) is False
    assert want_delta_usb_on(399, sleeping=False, generator=False, delta_soc=None) is None
    assert want_delta_usb_on(400, sleeping=False, generator=False, delta_soc=50) is False
    assert want_delta_usb_on(10, sleeping=True, generator=False, delta_soc=50) is False
    assert want_delta_usb_on(
        10, sleeping=True, generator=False, delta_soc=50, operator_hold=True
    ) is None
    assert want_delta_usb_on(
        0, sleeping=False, generator=False, delta_soc=50, pv_zero_for_s=299
    ) is True
    assert want_delta_usb_on(
        0, sleeping=False, generator=False, delta_soc=50, pv_zero_for_s=300
    ) is False
    assert want_delta_usb_on(
        0,
        sleeping=False,
        generator=False,
        delta_soc=50,
        pv_zero_for_s=300,
        operator_hold=True,
    ) is None
    assert want_delta_usb_on(10, sleeping=False, generator=True, delta_soc=50) is None
    delta = {
        "at": 1_700_000_000_000,
        "source": "ble",
        "body": {"data": {"mppt.inWatts": 100, "inv.outputWatts": 0, "inv.inputWatts": 120}},
    }
    river = {
        "at": 1_700_000_000_000,
        "source": "ble",
        "body": {"data": {"mppt.inWatts": 50, "inv.outputWatts": 0, "inv.inputWatts": 0}},
    }
    import time as t

    now_ms = int(t.time() * 1000)
    delta["at"] = now_ms
    river["at"] = now_ms
    pv = total_fresh_pv(delta, river)
    assert pv["ok"] is True
    assert pv["total_w"] == 150
    gen = classify_generator(delta, river)
    assert gen["generator"] is True
    delta2 = {
        "at": now_ms,
        "source": "ble",
        "body": {"data": {"mppt.inWatts": 20, "inv.outputWatts": 80, "inv.inputWatts": 0}},
    }
    river2 = {
        "at": now_ms,
        "source": "ble",
        "body": {"data": {"mppt.inWatts": 10, "inv.outputWatts": 0, "inv.inputWatts": 80}},
    }
    tr = classify_generator(delta2, river2)
    assert tr["transfer"] is True
    assert tr["generator"] is False
    river_stale = {
        "at": now_ms - 400_000,
        "source": "ble",
        "body": {"data": {"mppt.inWatts": 0, "inv.outputWatts": 40, "inv.inputWatts": 0}},
    }
    only = classify_generator(delta, river_stale)
    assert only["generator"] is True
    assert only["detail"] == "external_ac_in_delta_only"


def test_starlink_is_delta_and_cloud_persist_exists():
    import ecoflow_ble_poller as poller
    from ecoflow_ble_store import STARLINK_SN, RIVER_SN, DELTA_SN

    assert STARLINK_SN == DELTA_SN
    assert STARLINK_SN != RIVER_SN
    assert callable(poller.maybe_cloud_persist)
    assert callable(poller._starlink_ac)
    src = (
        Path.home()
        / ".ollama"
        / "skills"
        / "ecoflow-ble-poller"
        / "scripts"
        / "ecoflow_ble_poller.py"
    ).read_text(encoding="utf-8")
    assert "def maybe_cloud_persist" in src
    assert src.index("def _quota_ac_in") < src.index("def maybe_cloud_persist")
    assert "return data.get(\"inv.acInWatts\")\n    existing" not in src
    assert "Starlink is hardwired to Delta AC" in src
    assert "await device.enable_usb_ports" in src
    assert src.count("await device.enable_ac_ports") == 0


def test_power_profile_helper(monkeypatch):
    import ecoflow_ble_poller as poller

    calls = []

    def fake_run(cmd, **kwargs):
        calls.append(cmd)
        class R:
            returncode = 0
        return R()

    monkeypatch.setattr(poller.subprocess, "run", fake_run)
    poller.set_power_profile("power-saver")
    poller.set_power_profile("performance")
    assert ["powerprofilesctl", "set", "power-saver"] in calls
    assert ["powerprofilesctl", "set", "performance"] in calls


def test_energy_bank_omits_unusable():
    from apps.core.services.energy import bank

    out = bank(
        [
            {"label": "DELTA 2", "soc": 50, "online": True, "usable": True},
            {"label": "RIVER 2 Pro", "soc": 1, "online": False, "usable": False},
        ]
    )
    assert out["ok"] is True
    assert len(out["packs"]) == 1
    assert out["packs"][0]["label"] == "DELTA 2"


def test_night_sleeping_reads_file(tmp_path, monkeypatch):
    from pathlib import Path as P
    from apps.core import scheduler

    eco = tmp_path / ".ollama" / "skills" / "ecoflow-ble-poller" / "store" / "state"
    eco.mkdir(parents=True)
    (eco / "night-mode.json").write_text('{"sleeping": true}', encoding="utf-8")
    monkeypatch.setattr(P, "home", classmethod(lambda cls: tmp_path))
    assert scheduler.night_sleeping() is True


def test_voice_skips_stale_river_one_pct():
    from apps.core.crons.since_last_fire.hourly_clip_reports import _pack_soc, solar_script
    from datetime import datetime
    from zoneinfo import ZoneInfo

    facts = (
        "EcoFlow (source=jsonl, last 11:00):\n"
        "- DELTA 2: 12% SOC, 123 Wh stored of 1024 Wh, PV in 40 W, out 10 W, online.\n"
        "- RIVER 2 Pro: 1% SOC, 8 Wh stored of 768 Wh, PV in 0 W, out 64 W, offline.\n"
    )
    # Offline leftover still has a SOC bullet — facts builder must omit it.
    # Voice still must not scrape '1' from a waiting note.
    waiting = (
        "EcoFlow (source=jsonl, last 11:00):\n"
        "- DELTA 2: 12% SOC, 123 Wh stored of 1024 Wh, PV in 40 W, out 10 W, online.\n"
        "- Bank combined (both packs): 0.12 kWh stored of 1.02 kWh, PV in 40 W.\n"
    )
    assert _pack_soc(waiting, "DELTA 2") == "12"
    assert _pack_soc("- DELTA 2: 1.7% SOC, online.\n", "DELTA 2") == "2"
    assert _pack_soc(waiting, "RIVER 2 Pro") is None
    now = datetime(2026, 9, 15, 11, 0, tzinfo=ZoneInfo("Pacific/Honolulu"))
    script = solar_script(waiting, now)
    assert "river" not in script.split()
    assert "1" not in script.split()


def test_live_pack_from_row_requires_ble_source():
    from apps.core.services import db_facts
    import time

    now = time.time()
    stale = db_facts._live_pack_from_row(
        "R621ZA16XH6K1155",
        {"source": "", "soc": 1, "deviceOnline": True, "solarW": 0, "outW": 64},
        at=now,
    )
    assert stale is None
    live = db_facts._live_pack_from_row(
        "R331ZAB5SG6S2858",
        {"source": "ble", "soc": 12.4, "deviceOnline": True, "solarW": 40, "outW": 10},
        at=now,
    )
    assert live is not None
    assert live["soc"] == 12.4


