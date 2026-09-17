from apps.council.queue import MAX_KEEP_DONE, enqueue, load, save


def test_dedupe_same_voice_and_source(tmp_path, monkeypatch):
    monkeypatch.setattr("apps.council.queue.QUEUE_PATH", tmp_path / "queue.json")
    save({"jobs": []})
    a = enqueue(voice="bruce", prompt="one", chat_id=1, source_message_id=9, kind="speak")
    b = enqueue(voice="bruce", prompt="two", chat_id=1, source_message_id=9, kind="speak")
    c = enqueue(voice="ava", prompt="three", chat_id=1, source_message_id=9, kind="speak")
    assert a is not None and b is None and c is not None
    pending = [j for j in load()["jobs"] if j["status"] == "queued"]
    assert len(pending) == 2


def test_done_history_does_not_block_enqueue(tmp_path, monkeypatch):
    monkeypatch.setattr("apps.council.queue.QUEUE_PATH", tmp_path / "queue.json")
    jobs = [
        {"id": f"d{i:03d}", "voice": "ava", "status": "done", "kind": "speak", "prompt": "x"}
        for i in range(40)
    ]
    save({"jobs": jobs})
    nxt = enqueue(voice="bruce", prompt="chain", chat_id=1, kind="speak")
    assert nxt is not None
    stored = load()["jobs"]
    done = [j for j in stored if j.get("status") == "done"]
    assert len(done) <= MAX_KEEP_DONE
    assert any(j.get("id") == nxt["id"] for j in stored)
