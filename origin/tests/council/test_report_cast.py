from apps.council.report_cast import (
    caption_for,
    lookup,
    maybe_note,
    notify_report,
    readable_script,
    transcript_body,
)


def test_readable_clip_script():
    assert readable_script("system_report cpu 12 percent") == "system report cpu 12 percent"


def test_transcript_and_caption_explain_notes():
    cap = caption_for("nws")
    assert "NWS" in cap
    assert "Reply" in cap
    body = transcript_body("nws", "Wind advisory for Hawaiʻi County.")
    assert "transcript" in body.lower()
    assert "Wind advisory" in body


def test_boot_kind_is_catchup_only(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.report_cast.PATH", tmp_path / "cast.json")
    monkeypatch.setattr("apps.council.report_cast.CONFIG_DIR", tmp_path)
    sent: list = []

    def poster(kind, a, b):
        sent.append((kind, a, b))
        return {"ok": True, "result": {"message_id": 1}}

    out = notify_report(
        "boot",
        transcript="This is the Ava Core Root Record boot status.",
        cfg=object(),
        poster=poster,
    )
    assert out.get("skipped") is True
    assert out.get("detail") == "boot_catchup_only"
    assert sent == []


def test_cast_posts_audio_then_transcript(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.report_cast.PATH", tmp_path / "cast.json")
    monkeypatch.setattr("apps.council.report_cast.CONFIG_DIR", tmp_path)
    monkeypatch.setattr("apps.council.report_cast._chat_id", lambda cfg: "-100")
    wav = tmp_path / "nws.wav"
    wav.write_bytes(b"RIFF")
    sent: list[tuple] = []

    def poster(kind, a, b):
        sent.append((kind, a, b))
        return {"ok": True, "result": {"message_id": 10 + len(sent)}}

    out = notify_report(
        "nws",
        transcript="High surf warning, Oʻahu.",
        audio=wav,
        cfg=object(),
        poster=poster,
    )
    assert out["ok"] is True
    assert sent[0][0] == "audio"
    assert "High surf" in sent[1][1]
    assert lookup(11)["kind"] == "nws"
    again = notify_report(
        "nws",
        transcript="High surf warning, Oʻahu.",
        audio=wav,
        cfg=object(),
        poster=poster,
    )
    assert again.get("skipped") is True
    assert len(sent) == 2
    wav.write_bytes(b"RIFFxxxx")
    restitch = notify_report(
        "nws",
        transcript="High surf warning, Oʻahu.",
        audio=wav,
        cfg=object(),
        poster=poster,
    )
    assert restitch.get("skipped") is True
    assert len(sent) == 2


def test_reply_saves_note(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.report_cast.PATH", tmp_path / "cast.json")
    monkeypatch.setattr("apps.council.report_cast.NOTES", tmp_path / "notes.jsonl")
    monkeypatch.setattr("apps.council.report_cast.CONFIG_DIR", tmp_path)
    data_path = tmp_path / "cast.json"
    data_path.write_text('{"posts": {"44": {"kind": "system", "ts": 1}}, "last": {}}\n')
    posted: list[str] = []
    monkeypatch.setattr(
        "apps.council.telegram.send_message",
        lambda *a, **k: posted.append(a[2] if len(a) > 2 else k.get("text") or "") or {"ok": True},
    )
    monkeypatch.setattr("apps.council.report_cast._touch", lambda: None)

    class Cfg:
        def token_for(self, voice):
            return "t"

    ok = maybe_note(
        Cfg(),
        "-1",
        reply_id=44,
        text="Speak the NPU load too.",
        from_id=1,
        username="alexrs94",
        display="Alexander",
        note_id=90,
    )
    assert ok is True
    assert "Logged" in posted[0]
    notes = (tmp_path / "notes.jsonl").read_text()
    assert "NPU" in notes
    assert maybe_note(Cfg(), "-1", reply_id=99, text="nope", from_id=1, username="x", display="x") is False
