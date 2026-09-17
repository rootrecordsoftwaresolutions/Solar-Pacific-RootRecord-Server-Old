from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "hurricane-tracker" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import storm_track as st

RAMMB_FIXTURE = """
Forecast Track Time of Latest Forecast: 2026-09-16 18:00
Forecast Hour Latitude Longitude Intensity
0 17.6 149.7 40
12 21.0 149.0 55
24 23.5 146.5 60
48 25.5 141.5 65
120 39.2 148.8 50
Forecast Track Archive About Forecast Track
Track History Synoptic Time Latitude Longitude Intensity
2026-09-16 18:00 17.6 149.7 40
2026-09-16 12:00 16.5 149.5 35
2026-09-16 06:00 15.4 148.9 30
2026-09-15 12:00 14.9 148.9 25
About Track History Enhanced Infrared (IR) Imagery
"""

JTWC = (
    "TROPICAL STORM 16W (DUJUAN) WAS LOCATED NEAR 16.5N 149.5E. "
    "THE SYSTEM WAS MOVING WEST-NORTHWESTWARD AT 12 KNOTS. "
    "MAXIMUM SUSTAINED SURFACE WINDS WERE ESTIMATED AT 35 KNOTS"
)


def test_jtwc_moving_wnw():
    deg, kt = st.parse_jtwc_moving(JTWC)
    assert kt == 12
    assert deg == 292.5


def test_jtwc_stationary():
    deg, kt = st.parse_jtwc_moving("THE SYSTEM WAS NEARLY STATIONARY.")
    assert kt == 0
    assert deg is None


def test_dujuan_region_is_westpac_not_hawaii():
    region = st.ocean_region(16.5, 149.5)
    assert region["id"] in {"wpac", "wpac-japan"}
    assert "date line" in region["note"].lower() or "Japan" in region["note"]
    island, nm = st.nearest_hawaii_nm(16.5, 149.5)
    assert nm > 800
    assert island == "Līhuʻe"


def test_westbound_is_away_from_hawaii():
    assert st.hawaii_approach(16.5, 149.5, 292.5) == "away"
    toward_hi = st.bearing_deg(16.5, 149.5, st.LIHUE[0], st.LIHUE[1])
    assert st.hawaii_approach(16.5, 149.5, toward_hi) == "toward"


def test_rammb_history_and_forecast_japan_not_hawaii(tmp_path, monkeypatch):
    monkeypatch.setattr(st, "TRACKS_PATH", tmp_path / "storm-tracks.json")
    parsed = st.parse_rammb_tracks(RAMMB_FIXTURE)
    assert len(parsed["history"]) >= 3
    assert parsed["history"][-1]["lat"] == 17.6
    assert parsed["forecast"][-1]["lat"] == 39.2
    storm = {
        "id": "wp242026",
        "lat": 17.6,
        "lon": 149.7,
        "knots": 40,
        "track_history": parsed["history"],
        "forecast_track": parsed["forecast"],
    }
    st.attach_track(storm, persist=True)
    assert storm["hawaii_approach"] in {"away", "abeam"}
    assert storm["forecast_closer_to_hawaii"] is False
    assert storm["forecast_end_hawaii_nm"] > 800
    assert "Japan" in storm["forecast_summary"] or "Western" in storm["forecast_summary"]
    assert "landfall" in storm["forecast_summary"].lower()


def test_course_from_fixes_north():
    course = st.course_from_fixes(
        [
            {"ts": "2026-09-16 12:00", "lat": 16.5, "lon": 149.5},
            {"ts": "2026-09-16 18:00", "lat": 17.6, "lon": 149.7},
        ]
    )
    assert course["course_compass"] in {"north", "northeast", "northwest"}
    assert course["course_kt"] and course["course_kt"] >= 8
