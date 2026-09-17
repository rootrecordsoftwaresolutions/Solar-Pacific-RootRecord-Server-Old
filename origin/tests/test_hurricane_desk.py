from apps.core.services.hurricane_desk import hawaii_block


def test_hawaii_block_remote_wp_has_track_and_no_threat():
    storms = [
        {
            "id": "wp242026",
            "name": "DUJUAN",
            "label": "Tropical Storm Dujuan",
            "lat": 17.6,
            "lon": 149.7,
            "knots": 40,
            "nearest_hawaii_nm": 2904,
            "hawaii_nm": {"Līhuʻe": 2904, "Honolulu": 2991},
            "basin": "wp",
            "region_name": "Western North Pacific (Japan / East China / Philippine Sea)",
            "region_note": "West of the date line, Asia/Japan side.",
            "movement_compass": "north",
            "movement_kt": 11,
            "hawaii_approach": "away",
            "forecast_summary": "RAMMB forecast on file ends 39.2N 148.8E. Forecast stays away from Hawaiʻi. Not a landfall call.",
        }
    ]
    hi = hawaii_block(storms, {"products": []})
    assert hi["hawaii_threat"] is False
    assert "not a Hawaiʻi threat" in hi["title"] or "not a Hawai" in hi["title"]
    assert "17.6N" in hi["spoken"]
    assert "away from Hawai" in hi["spoken"]
    assert "RAMMB forecast" in hi["spoken"]
    assert "Do not treat this as a Hawaiʻi local storm" in hi["spoken"]
