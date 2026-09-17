from apps.council import refer


def test_parse_offer_rejects_self_and_adult(tmp_path, monkeypatch):
    monkeypatch.setattr(refer, "PATH", tmp_path / "refer.json")
    assert refer.parse_offer(
        "<<<OFFER voice=bruce topic=market gardening in rocky soil>>>",
        from_voice="ava",
    ) == {"to": "bruce", "topic": "market gardening in rocky soil"}
    assert refer.parse_offer("<<<OFFER voice=ava topic=brand>>>", from_voice="ava") is None
    assert refer.parse_offer("<<<OFFER voice=carly topic=nsfw roleplay>>>", from_voice="ava") is None


def test_yes_dispatches_other_bot(tmp_path, monkeypatch):
    monkeypatch.setattr(refer, "PATH", tmp_path / "refer.json")
    sent = []

    monkeypatch.setattr(
        refer.telegram,
        "send_message",
        lambda token, chat, text, **k: sent.append((chat, text)) or {"ok": True, "result": {"message_id": 1}},
    )

    class C:
        def token_for(self, voice):
            return voice

    cfg = C()
    refer.note_offer(
        "6644",
        "<<<OFFER voice=bruce topic=philosophy of robotics>>>",
        from_voice="ava",
    )
    assert refer.maybe_consume(cfg, "6644", "Yes please") == "sent"
    assert sent
    body = sent[0][1].lower()
    assert "ava" in body
    assert "philosophy of robotics" in body
    assert "bruce" in body
    assert "in depth" in body or "deeper" in body or "depth" in body


def test_no_clears_offer(tmp_path, monkeypatch):
    monkeypatch.setattr(refer, "PATH", tmp_path / "refer.json")
    refer.note_offer(
        "1",
        "<<<OFFER voice=carly topic=account safety>>>",
        from_voice="ava",
    )
    class C:
        def token_for(self, voice):
            return "t"

    assert refer.maybe_consume(C(), "1", "nah") == "cleared"
    assert refer.pending("1") is None


def test_team_prompt_forbids_dodge():
    p = refer.team_prompt("ava")
    assert "Answer their question yourself first" in p
    assert "Bruce" in p and "Carly" in p
    assert "OFFER" in p
    assert "clubs" in p.lower()
