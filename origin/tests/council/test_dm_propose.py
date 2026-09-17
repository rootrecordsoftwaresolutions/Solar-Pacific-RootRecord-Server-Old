from apps.council import dm_propose
from apps.council.config import Config


def test_parse_propose_and_share_ask():
    raw = "Cute idea. <<<PROPOSE a local coping-prompt tool for DMs>>>"
    assert "coping-prompt" in dm_propose.parse_propose(raw)
    assert dm_propose.looks_share_ask("You should suggest that to your peers in the group chat")
    assert not dm_propose.looks_share_ask("I like talking here")


def test_lift_uses_group_not_dm_transcript(tmp_path, monkeypatch):
    sent = []
    docs = []

    monkeypatch.setattr(dm_propose, "PATH", tmp_path / "dm-lifts.json")
    monkeypatch.setattr(
        dm_propose.telegram,
        "send_message",
        lambda *a, **k: sent.append((a, k)) or {"ok": True, "result": {"message_id": 1}},
    )
    monkeypatch.setattr(
        dm_propose.telegram,
        "send_document",
        lambda *a, **k: docs.append((a, k)) or {"ok": True, "result": {"message_id": 9}},
    )

    def _publish(*, chat_id, origin, thread_id="", notes=""):
        assert str(chat_id) == "-1001"
        assert "private" not in origin.lower() or "DM idea" in origin
        assert "secret dm line" not in origin
        assert "Private thread was not copied" in notes
        path = tmp_path / "plan-x.md"
        path.write_text("# plan\n", encoding="utf-8")
        return {"id": "plan-x", "path": str(path)}

    monkeypatch.setattr(dm_propose.proposals, "publish", _publish)
    monkeypatch.setattr(dm_propose.proposals, "remember_telegram_message", lambda *a, **k: None)
    import apps.council.people as people_mod

    monkeypatch.setattr(
        people_mod,
        "dossier",
        lambda uid: {"display_name": "WildEcho94", "username": "WildEcho94", "features": {}},
    )
    monkeypatch.setattr(dm_propose.trust, "load_trust", lambda: {"users": {}})
    monkeypatch.setattr(dm_propose.trust, "get_score", lambda data, uid: 50)

    cfg = Config()
    st = {"group_chat_id": "-1001"}
    out = dm_propose.maybe_lift(
        cfg,
        st,
        voice="ava",
        raw="On it. <<<PROPOSE a questionnaire that offers coping prompts>>>",
        meta={
            "dm": True,
            "judge_user_id": "6644482344",
            "user_text": "You should suggest that to your peers in the group chat",
        },
        dm_chat_id="6644482344",
    )
    assert out.get("ok")
    assert sent, "group + dm confirm"
    group_hits = [a for a, _ in sent if str(a[1]) == "-1001"]
    assert group_hits
    assert docs


def test_skip_nsfw_and_low_trust(monkeypatch, tmp_path):
    monkeypatch.setattr(dm_propose, "PATH", tmp_path / "dm-lifts.json")
    cfg = Config()
    st = {"group_chat_id": "-1001"}
    nsfw = dm_propose.maybe_lift(
        cfg,
        st,
        voice="ava",
        raw="<<<PROPOSE nsfw thing>>>",
        meta={"dm": True, "nsfw": True, "judge_user_id": "1", "user_text": "post this to group"},
        dm_chat_id="1",
    )
    assert nsfw["detail"] == "nsfw"
    monkeypatch.setattr(dm_propose.trust, "load_trust", lambda: {})
    monkeypatch.setattr(dm_propose.trust, "get_score", lambda *a, **k: 20)
    low = dm_propose.maybe_lift(
        cfg,
        st,
        voice="ava",
        raw="<<<PROPOSE a garden club on the public site>>>",
        meta={"dm": True, "judge_user_id": "1", "user_text": "share this with the group"},
        dm_chat_id="1",
    )
    assert low["detail"] == "trust"
