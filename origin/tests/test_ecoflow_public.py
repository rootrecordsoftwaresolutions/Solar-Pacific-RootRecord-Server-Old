from apps.core.services import ecoflow_public as pub
import json


def test_rising_edge_announce(monkeypatch, tmp_path):
    monkeypatch.setattr(pub, "live_path", lambda: tmp_path / "ecoflow-live.json")
    monkeypatch.setattr(pub, "prior_path", lambda: tmp_path / "generator-announce.json")
    posts = []
    monkeypatch.setattr(
        "apps.council.notify.post",
        lambda voice, text: posts.append((voice, text)) or {"ok": True},
    )
    monkeypatch.setattr(
        "apps.core.services.hybrid_reports.update_hybrid_daily_report",
        lambda: {"ok": True},
    )
    gate = {"delta_soc": 16.0, "total_pv_w": 0, "last_decision": "hold_generator"}
    gen = {"generator": True, "transfer": False, "detail": "external_ac_in", "delta_ac_in_w": 679}
    first = pub.on_gate_update(gate=gate, gen=gen)
    assert first["sent"] is True
    assert posts and posts[0][0] == "bruce"
    assert "679" in posts[0][1] or "GENERATOR ON" in posts[0][1]
    live = pub.live_path().read_text(encoding="utf-8")
    assert "R331" not in live
    second = pub.on_gate_update(gate=gate, gen=gen)
    assert second["sent"] is False
    pub.on_gate_update(gate=gate, gen={"generator": False, "transfer": False, "detail": "idle"})
    third = pub.on_gate_update(gate=gate, gen=gen)
    assert third["sent"] is True


def test_ceiling_not_setpoint_and_over_flags():
    snap = pub.snapshot(
        gate={"delta_soc": 20, "total_pv_w": 0},
        gen={"generator": True, "delta_ac_in_w": 679, "river_ac_in_w": 0},
    )
    assert snap["caps"]["kind"] == "ceiling"
    assert snap["delta"]["ceiling_w"] == 750
    assert snap["over_ceiling"] is False
    high = pub.snapshot(
        gate={"delta_soc": 20},
        gen={"generator": True, "delta_ac_in_w": 900},
    )
    assert high["over_ceiling"] is True
    assert "FAULT" in pub.format_status(high) or "ceiling" in pub.format_status(high).lower()


def test_desk_card_ble_gone_and_stale_detail():
    gone = pub.snapshot(
        gate={"delta_soc": 56, "total_pv_w": 0, "quota_source": ""},
        gen={"generator": False, "transfer": False, "detail": "external_ac_in"},
    )
    assert gone["generator"] is False
    assert gone["detail"] in {"ble_gone", "idle"}
    assert gone["ble_gone"] is True
    assert "Unknown" in gone["card"] or "unknown" in gone["card"].lower()
    leftover = pub.snapshot(
        gate={"delta_soc": 1.0, "total_pv_w": 0, "quota_source": "cloud"},
        gen={"generator": False, "transfer": False, "detail": "idle"},
    )
    assert leftover["delta"]["soc"] is None


def test_overlay_sanitizes_stale_external_ac_in(tmp_path, monkeypatch):
    live = tmp_path / "ecoflow-live.json"
    live.write_text(
        json.dumps(
            {
                "generator": False,
                "transfer": False,
                "detail": "external_ac_in",
                "delta": {"soc": 56, "ac_in_w": None, "source": ""},
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setattr(pub, "live_path", lambda: live)
    out = pub.overlay_board({"battery_pct": 56, "ok": True})
    assert out["generator"] is False
    assert out["ble_gone"] is True
    assert out["generator_detail"] == "ble_gone"
    assert "unknown" in (out["generator_card"] or "").lower()


def test_one_pack_generator_keeps_detail():
    snap = pub.snapshot(
        gate={"delta_soc": 40, "total_pv_w": 0, "quota_source": "ble"},
        gen={
            "generator": True,
            "transfer": False,
            "detail": "external_ac_in_delta_only",
            "delta_ac_in_w": 400,
        },
    )
    assert snap["generator"] is True
    assert snap["detail"] == "external_ac_in_delta_only"
    assert snap["ble"] is True
    assert "GENERATOR ON" in snap["card"]


def test_hybrid_insert_once_per_activation(tmp_path, monkeypatch):
    from datetime import datetime
    from zoneinfo import ZoneInfo

    from apps.core.services import hybrid_reports as hr

    live = tmp_path / "ecoflow-live.json"
    live.write_text(
        '{"generator": true, "transfer": false, "delta": {"ac_in_w": 412}}\n',
        encoding="utf-8",
    )
    monkeypatch.setattr("apps.core.services.ecoflow_public.live_path", lambda: live)
    monkeypatch.setattr("apps.core.config.DATA_DIR", tmp_path)
    monkeypatch.setattr("apps.core.config.STATE_DIR", tmp_path / "state")
    now = datetime(2026, 9, 15, 18, 0, tzinfo=ZoneInfo("Pacific/Honolulu"))
    first = hr._generator_insert(now)
    assert first and "412 W" in first and "GENERATOR" in first
    second = hr._generator_insert(now)
    assert second is None
    live.write_text('{"generator": false, "transfer": false}\n', encoding="utf-8")
    assert hr._generator_insert(now) is None
    live.write_text(
        '{"generator": true, "transfer": false, "delta": {"ac_in_w": 400}}\n',
        encoding="utf-8",
    )
    third = hr._generator_insert(now)
    assert third and "400 W" in third
