import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "hurricane-desk" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import storm_plot as sp
from apps.council.skills import match_exec_skill


def test_storm_plot_dujuan_not_hawaii(monkeypatch, tmp_path):
    monkeypatch.setattr(sp, "STATE", tmp_path)
    (tmp_path / "hurricane-desk.json").write_text(
        '{"hawaii":{"label":"Tropical Storm Dujuan","name":"DUJUAN","nm":2904,'
        '"island":"Līhuʻe","bearing":"west","nws_tropical":[],"alerts":[],"basin":"wp"}}',
        encoding="utf-8",
    )
    (tmp_path / "hurricanes.json").write_text(
        '{"storms":[{"name":"DUJUAN","label":"Tropical Storm Dujuan","lat":16.5,"lon":149.5,'
        '"nearest_hawaii_nm":2904,"basin_name":"Western Pacific",'
        '"hawaii_nm":{"Līhuʻe":2904,"Honolulu":2991,"Hilo":3154,"Kona":3104},'
        '"region_name":"Western North Pacific","hawaii_approach":"away",'
        '"movement_compass":"north","movement_kt":11,'
        '"track_history":[{"ts":"2026-09-16 12:00","lat":16.5,"lon":149.5},'
        '{"ts":"2026-09-16 18:00","lat":17.6,"lon":149.7}],'
        '"forecast_summary":"RAMMB forecast on file ends 39.2N 148.8E (Western North Pacific). Forecast stays away from Hawaiʻi. Not a landfall call."}]}',
        encoding="utf-8",
    )
    text = sp.plot_text()
    assert "2904" in text
    assert "threat: NO" in text
    assert "Japan" in text or "Asia" in text
    assert "16.5N" in text and "149.5E" in text
    assert "*" in text
    assert "Region: Western North Pacific" in text
    assert "Motion:" in text
    assert "away" in text
    assert "RAMMB forecast" in text
    assert match_exec_skill("how far is the storm")["id"] == "storm-plot"


def test_storm_plot_live_file_has_dujuan():
    line = sp.prompt_line()
    assert "Hawai" in line or "No mapped" in line or "threat" in line.lower()
