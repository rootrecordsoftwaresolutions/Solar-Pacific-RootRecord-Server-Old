from apps.council.burst import (
    WAIT_S,
    as_update,
    combined_text,
    has_pending,
    ingest,
    poll_timeout,
    pop_due,
    stash_idle,
)


def test_dm_burst_combines_and_persists(monkeypatch, tmp_path):
    path = tmp_path / "burst.json"
    monkeypatch.setattr("apps.council.burst.PATH", path)
    monkeypatch.setattr("apps.council.burst.CONFIG_DIR", tmp_path)
    ingest(
        chat_id=9,
        user_id=77,
        listen_voice="ava",
        private=True,
        message={"chat": {"id": 9, "type": "private"}, "message_id": 1, "text": "hey"},
        text="hey",
        user={"id": 77, "username": "wildecho94"},
    )
    ingest(
        chat_id=9,
        user_id=77,
        listen_voice="ava",
        private=True,
        message={"chat": {"id": 9, "type": "private"}, "message_id": 2, "text": "you there"},
        text="you there",
        user={"id": 77, "username": "wildecho94"},
    )
    assert has_pending(9, 77, "ava")
    assert path.is_file()
    due = pop_due(now=1e18)
    assert len(due) == 1
    blob = combined_text(due[0])
    assert "hey" in blob
    assert "you there" in blob
    upd = as_update(due[0])
    assert upd["message"]["text"] == blob
    assert upd["message"]["message_id"] == 2
    assert not has_pending(9, 77, "ava")


def test_idle_then_address_joins(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.burst.PATH", tmp_path / "burst.json")
    monkeypatch.setattr("apps.council.burst.CONFIG_DIR", tmp_path)
    stash_idle(chat_id=-1, user_id=5, listen_voice="ava", text="hold on", message_id=10)
    ingest(
        chat_id=-1,
        user_id=5,
        listen_voice="ava",
        private=False,
        message={"chat": {"id": -1, "type": "supergroup"}, "message_id": 11, "text": "team check this"},
        text="team check this",
        user={"id": 5},
    )
    due = pop_due(now=1e18)
    blob = combined_text(due[0])
    assert "hold on" in blob
    assert "team check this" in blob


def test_poll_timeout_shrinks_when_due(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.burst.PATH", tmp_path / "burst.json")
    monkeypatch.setattr("apps.council.burst.CONFIG_DIR", tmp_path)
    ingest(
        chat_id=1,
        user_id=1,
        listen_voice="ava",
        private=True,
        message={"message_id": 3, "text": "a"},
        text="a",
        user={"id": 1},
    )
    t = poll_timeout(25)
    assert 0 <= t <= int(WAIT_S) + 1
    assert poll_timeout(25, drain=True) == 0
