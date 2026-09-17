from apps.council import asked_today, people, router


def test_extract_and_answer_cycle(monkeypatch, tmp_path):
    monkeypatch.setattr(asked_today, "PATH", tmp_path / "responses-today.json")
    monkeypatch.setattr(asked_today, "CONFIG_DIR", tmp_path)
    text = "Ava, did you want to take a look at the PR calendar for the week?"
    asks = asked_today.extract_asks(text, from_id="bruce")
    assert asks and asks[0]["to"] == "ava"
    fresh = asked_today.note_asks("bruce", text)
    assert fresh
    assert asked_today.open_asks_for("ava")
    n = asked_today.note_answer("ava", "Yes — PR calendar looks clear this week.")
    assert n >= 1
    assert asked_today.open_asks_for("ava") == []
    # same ask should not re-open follow
    assert asked_today.follow_targets(text, from_voice="bruce") == []


def test_team_chain_order():
    assert router.team_chain_order(["carly", "bruce", "ava"]) == ["ava", "bruce", "carly"]


def test_vocative_agent_call():
    calls = router.detect_agent_calls_in_reply(
        "Ava, did you want to take a look at the PR calendar?",
        from_voice="bruce",
    )
    assert "ava" in calls
    assert "bruce" not in calls


def test_agent_dossier_like_people(monkeypatch, tmp_path):
    monkeypatch.setattr(people, "PATH", tmp_path / "people.json")
    monkeypatch.setattr(people, "CONFIG_DIR", tmp_path)
    people.ensure_agents()
    row = people.agent_dossier("ava")
    assert row["display_name"] == "Ava"
    people.observe_agent("ava", "Good morning, Alexander.")
    again = people.agent_dossier("ava")
    assert again["messages"] == 1
    assert "Good morning" in again["last_snippet"]
    blob = people.agent_prompt_block("bruce")
    assert "Ava" in blob
    assert "You (bruce)" in blob or "Bruce" in blob
