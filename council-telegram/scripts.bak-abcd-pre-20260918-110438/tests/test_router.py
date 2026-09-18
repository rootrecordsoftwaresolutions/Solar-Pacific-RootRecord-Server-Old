from apps.council.router import (
    apply_reply_context,
    detect_addressing,
    detect_callouts,
    is_empathy,
    remaining_round_order,
)


def test_question_keywords_team_round():
    from apps.council.router import is_direct_question

    for line in (
        "how do I vote?",
        "what is RootMC",
        "what's the solar status",
        "can you explain the packs",
        "help me find the invite",
        "where do I start",
        "why is the radio down?",
    ):
        assert is_direct_question(line), line
        addr = detect_addressing(line)
        assert addr["voices"] == ["ava"], line
        assert addr["reason"] == "question"
        assert not addr.get("round")


def test_what_is_ava_still_answers():
    addr = detect_addressing("what is Ava's job here")
    assert addr["reason"] == "question"
    assert addr["voices"] == ["ava"]


def test_hold_on_sara_silence():
    addr = detect_addressing("hold on @Crazychickenlady12")
    assert addr["voices"] == []
    assert addr["reason"] in ("silence", "third_person")
    assert not addr.get("round")


def test_third_person_ava_is_quiet():
    addr = detect_addressing("She's the security expert. Ava is the primary PR agent.")
    assert addr["voices"] == []
    assert addr["reason"] == "third_person"


def test_ava_role_third_person():
    assert detect_callouts("Ava's role is public wording") == []
    assert detect_callouts("ask Ava later") == []


def test_hey_ava_vocative():
    addr = detect_addressing("Hey Ava can you greet Sara?")
    assert addr["voices"] == ["ava"]
    assert addr["reason"] == "vocative"
    assert not addr.get("round")


def test_team_plus_named_bruce_only():
    addr = detect_addressing("Thanks team. Bruce, taking notes?")
    assert addr["voices"] == ["bruce"]
    assert addr["reason"] == "team_override"
    assert not addr.get("round")


def test_hey_guys_queues_all_three():
    addr = detect_addressing("hey guys")
    assert addr["voices"] == ["ava", "bruce", "carly"]
    assert addr["reason"] == "team_all"
    assert not addr.get("round")


def test_all_feeling_queues_all_three():
    addr = detect_addressing("hey team, how are we all feeling?")
    assert addr["voices"] == ["ava", "bruce", "carly"]
    assert addr["reason"] == "team_all"
    assert not addr.get("round")


def test_agents_and_ai_keywords():
    for line in (
        "Agents, status?",
        "what do the AI think",
        "ok everyone",
        "talk to the agents",
    ):
        addr = detect_addressing(line)
        assert addr["voices"] == ["ava", "bruce", "carly"], line
        assert addr["reason"] == "team_all", line


def test_thought_session_ava_starts_round():
    addr = detect_addressing(
        "Hey team, lets test a thought session for proposals. Ava, you start..."
    )
    assert addr["voices"] == ["ava"]
    assert addr["round"] is True
    assert addr["reason"] == "round_start"
    assert addr["round_order"][0] == "ava"
    assert "bruce" in addr["round_order"]


def test_pass_on_plus_reply_to_ava():
    addr = detect_addressing("You're supposed to pass this onto the others first.")
    addr = apply_reply_context(
        addr,
        {"username": "avaivy_bot", "is_bot": True},
        "You're supposed to pass this onto the others first.",
    )
    assert addr["reason"] == "round_pass"
    assert addr["round"] is True
    assert addr["voices"] == ["bruce"]
    assert remaining_round_order("ava") == ["bruce", "carly", "ava"]


def test_reply_continue_without_pass_on():
    addr = detect_addressing("that was a bit much")
    addr = apply_reply_context(addr, {"username": "carlymal_bot"}, "that was a bit much")
    assert addr["voices"] == ["carly"]
    assert not addr.get("round")


def test_untagged_human_stays_quiet():
    addr = detect_addressing("it is")
    addr = apply_reply_context(addr, None, "it is")
    assert addr["voices"] == []


def test_substantive_untagged_stays_quiet():
    addr = detect_addressing(
        "Just adding some context, the delta 2 only allows setting to the rounded 100s."
    )
    assert addr["voices"] == []
    assert addr["reason"] == "silence"
    assert not addr.get("round")


def test_empathy_i_can_relate():
    assert is_empathy("I can relate to those feelings a lot of the time")


def test_carly_tag():
    addr = detect_addressing("@carlymal_bot what is Kiweh?")
    assert addr["voices"] == ["carly"]


def test_bare_council_not_group():
    addr = detect_addressing("the architecture council at work was loud")
    assert addr["voices"] == []
    assert addr["reason"] == "silence"


def test_over_to_carly_handoff():
    addr = detect_addressing("over to Carly")
    assert addr["voices"] == ["carly"]
    assert not addr.get("round")


def test_good_job_everyone_thanks_carly():
    addr = detect_addressing(
        "Good job everyone. And thank you Carly for respecting our token usage. "
        "It's important not to over spend. Much appreciated."
    )
    assert addr["group"] is True
    assert addr["voices"] == ["ava", "bruce", "carly"]
    assert addr["reason"] == "team_all"
