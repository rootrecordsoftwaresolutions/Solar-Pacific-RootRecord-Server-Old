from apps.core.services import kilauea


def test_publish_fingerprint_ignores_hvo_when_notice_id_present():
    a = kilauea._publish_fingerprint("DOI-1", "watch body A", "watch")
    b = kilauea._publish_fingerprint("DOI-1", "watch body B with new quakes listed", "watch")
    assert a == b


def test_publish_fingerprint_changes_on_new_notice_or_alert():
    base = kilauea._publish_fingerprint("DOI-1", "x", "watch")
    assert kilauea._publish_fingerprint("DOI-2", "x", "watch") != base
    assert kilauea._publish_fingerprint("DOI-1", "x", "warning") != base


def test_publish_fingerprint_empty_notice_ignores_hvo_clock():
    a = kilauea._publish_fingerprint("", "as of 09:00", "normal")
    b = kilauea._publish_fingerprint("", "as of 10:00", "normal")
    assert a == b


def test_decide_publish_no_notice_does_not_seed(monkeypatch, tmp_path):
    monkeypatch.setattr(kilauea, "_publish_path", lambda: tmp_path / "kilauea-publish.json")
    should, _, reason = kilauea.decide_publish("", "excerpt", "normal")
    assert should is False
    assert reason == "no-notice"


def test_infer_alert_level_ignores_usgs_magnitudes():
    features = [{"properties": {"mag": 5.4}}]
    assert kilauea._infer_alert_level(features, "") == "normal"
    assert kilauea._infer_alert_level(features, "Current volcano alert level: watch") == "watch"


def test_decide_publish_seeds_then_skips_until_notice_changes(monkeypatch, tmp_path):
    monkeypatch.setattr(kilauea, "_publish_path", lambda: tmp_path / "kilauea-publish.json")
    should, fp, reason = kilauea.decide_publish("DOI-1", "watch", "watch")
    assert should is False
    assert reason == "seed"
    kilauea.remember_publish(fp, "DOI-1", "watch")
    should2, _, reason2 = kilauea.decide_publish("DOI-1", "different excerpt", "watch")
    assert should2 is False
    assert reason2 == "unchanged"
    should3, _, reason3 = kilauea.decide_publish("DOI-2", "watch", "watch")
    assert should3 is True
    assert reason3 == "changed"
