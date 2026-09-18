from apps.council.skills import match_exec_skill


def test_radar_keyword_matches_bruce_skill():
    hit = match_exec_skill("show me the latest radar")
    assert hit and hit["id"] == "radar-gif"
    assert "bruce" in [str(v).lower() for v in (hit.get("voices") or [])]


def test_weather_radar_phrase():
    hit = match_exec_skill("any weather radar for Hawaii?")
    assert hit and hit["id"] == "radar-gif"
