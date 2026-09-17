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
        / "river_car_dc.py"
    )
    spec = spec_from_file_location("river_car_dc", p)
    m = module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(m)
    return m


def test_car_command_is_mppt_car_not_ac():
    m = _mod()
    on = m.car_command(True)
    off = m.car_command(False)
    assert on["operateType"] == "mpptCar"
    assert off["operateType"] == "mpptCar"
    assert on["moduleType"] == 5
    assert on["params"]["enabled"] == 1
    assert off["params"]["enabled"] == 0
    blob = str(on) + str(off)
    assert "acOutCfg" not in blob
    assert "enable_ac" not in blob


def test_car_state_from_quota_keys():
    m = _mod()
    on, key = m.car_state({"pd.carState": 1})
    assert on is True
    assert key == "pd.carState"
    off, _ = m.car_state({"mppt.carState": 0})
    assert off is False
    unknown, _ = m.car_state({})
    assert unknown is None


def test_dry_run_does_not_put(tmp_path, monkeypatch):
    m = _mod()
    monkeypatch.setattr(m, "state_path", lambda: tmp_path / "river-car-dc.json")
    monkeypatch.setattr(m, "put_car", lambda turn_on: (_ for _ in ()).throw(AssertionError("put")))
    out = m.set_car(want_on=True, execute=False)
    assert out["ok"] is True
    assert out["backup_job"] is False
    assert out["action"] == "dry_run_on"
    st = m.load_state()
    assert st["wanted"] is True
    assert st["purpose"] == "external-drives"
