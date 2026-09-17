from apps.core.services import report_blog, reports


def test_blog_skips_identical_kilauea_body(monkeypatch, tmp_path):
    monkeypatch.setattr(report_blog, "_body_state_path", lambda: tmp_path / "body.json")
    monkeypatch.setattr(report_blog, "posts_root", lambda: tmp_path / "posts")
    monkeypatch.setattr(report_blog, "run_sync_blogs", lambda: False)
    text = "Kīlauea status update. The current volcano alert level is watch. End of status."
    first = report_blog.publish_report_post(
        report_type="kilauea", text=text, engine="local", sync=False
    )
    assert first.get("ok") is True
    assert not first.get("skipped")
    second = report_blog.publish_report_post(
        report_type="kilauea", text=text, engine="local", sync=False
    )
    assert second.get("skipped") is True
    assert second.get("detail") == "unchanged"


def test_generated_text_gate(monkeypatch, tmp_path):
    monkeypatch.setattr(reports, "CONTENT_STATE_PATH", tmp_path / "content.json")
    body = "Midday status. Same words."
    assert reports.generated_text_is_new("midday", body) is True
    reports.mark_generated_text("midday", body)
    assert reports.generated_text_is_new("midday", body) is False
    assert reports.generated_text_is_new("midday", body + " new") is True
