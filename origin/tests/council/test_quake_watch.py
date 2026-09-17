from apps.council.quake_watch import format_quake, process_feed, spoken_quake


def test_format_quake_uses_usgs_fields(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.quake_watch.PATH", tmp_path / "quake-watch.json")
    text = format_quake(
        {
            "id": "hv123",
            "mag": 3.2,
            "place": "14 km S of Pahala, Hawaii",
            "time": 1758000000000,
            "depth_km": 8.4,
        },
        scope="island",
    )
    assert "M 3.2" in text
    assert "Pahala" in text
    assert "eventpage/hv123" in text
    assert "Ava Ivy" not in text
    assert "USGS" in text
    assert "8.4 km" in text


def test_spoken_quake_expands_for_voice():
    spoken = spoken_quake(
        {
            "id": "hv123",
            "mag": 3.2,
            "place": "14 km SSW of Pahala, Hawaii",
            "time": 1758000000000,
            "depth_km": 8,
        },
        scope="island",
    )
    assert spoken.startswith("Earthquake.")
    assert "Magnitude 3.2" in spoken
    assert "14 km SSW of Pahala" in spoken
    assert "Depth 8 kilometers" in spoken
    assert "U. S. Geological Survey Hawaii" in spoken


def test_process_feed_seeds_without_posting(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.quake_watch.PATH", tmp_path / "quake-watch.json")
    posted: list[tuple] = []

    def fake_deliver(q, *, scope):
        posted.append((scope, q.get("id"), q.get("place")))
        return {"ok": True}

    monkeypatch.setattr("apps.council.quake_watch.deliver_quake", fake_deliver)
    feed = {
        "island": [{"id": "a1", "mag": 2.4, "place": "Island", "time": 1}],
        "global": [{"id": "g1", "mag": 5.1, "place": "Pacific", "time": 2}],
    }
    out = process_feed(feed)
    assert out.get("seeded") is True
    assert posted == []
    out2 = process_feed(feed)
    assert out2.get("count") == 0
    feed2 = {
        "island": [
            {"id": "a1", "mag": 2.4, "place": "Island", "time": 1},
            {"id": "a2", "mag": 2.1, "place": "New", "time": 3}],
        "global": [{"id": "g1", "mag": 5.1, "place": "Pacific", "time": 2}],
    }
    out3 = process_feed(feed2)
    assert out3.get("count") == 1
    assert posted[0][1] == "a2"
    assert posted[0][2] == "New"
