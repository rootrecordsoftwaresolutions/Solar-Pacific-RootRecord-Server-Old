from __future__ import annotations

from datetime import datetime, timedelta, timezone

from apps.core.services import nws_hawaii


def test_hawaii_county_tropical_watch_or_warning_triggers_replay():
    assert nws_hawaii.hawaii_county_tropical_watch_warning(
        [{"event": "Tropical Storm Watch", "counties": ["hawaii"]}]
    ) is True
    assert nws_hawaii.hawaii_county_tropical_watch_warning(
        [{"event": "Tropical Storm Warning", "counties": ["hawaii"]}]
    ) is True


def test_tropical_replay_ignores_other_counties_and_events():
    assert nws_hawaii.hawaii_county_tropical_watch_warning(
        [{"event": "Tropical Storm Warning", "counties": ["maui"]}]
    ) is False
    assert nws_hawaii.hawaii_county_tropical_watch_warning(
        [{"event": "Flood Warning", "counties": ["hawaii"]}]
    ) is False


def test_quiet_spoken_has_no_wall_clock():
    text = nws_hawaii.build_spoken(
        {"honolulu": [], "hawaii": [], "maui": [], "kauai": [], "kalawao": []}
    )
    assert "about" not in text.lower()
    assert "Hawaiian Standard Time" not in text
    assert text.startswith("NWS Hawaii by county.")
    old = (datetime.now(timezone.utc) - timedelta(minutes=31)).isoformat()
    assert nws_hawaii._seconds_since(old) >= 1800
