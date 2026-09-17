from apps.council.feelings import prompt_block
from apps.council.prompting import build_round_follow_prompt, build_speak_prompt, quoted_from_message


def test_feelings_prompt_has_mood_not_gauges():
    block = prompt_block("carly")
    assert "mood=" in block
    assert "dryness=" not in block
    assert "0–100" not in block


def test_reply_quote_included():
    msg = {
        "reply_to_message": {
            "from": {"username": "Crazychickenlady12", "first_name": "Sara"},
            "text": "what does Kiweh mean vs Kileau",
        }
    }
    q = quoted_from_message(msg)
    assert "Sara" in q or "Crazychickenlady12" in q
    assert "Kiweh" in q


def test_round_follow_brainstorm_skips_unrelated_desk(monkeypatch):
    monkeypatch.setattr("apps.council.brainstorm.status", lambda: "running")
    monkeypatch.setattr("apps.council.brainstorm.topic", lambda: "NPU soak")
    p = build_round_follow_prompt(
        voice="bruce",
        prev_voice="ava",
        prev_text="Kīlauea is at WATCH and rain is coming. Also cybersecurity.",
        origin_text="Stay on this topic only: NPU soak.",
        chat_id=-1,
    )
    assert "Stay on this topic only: NPU soak" in p
    assert "do not follow them" in p.lower()
    assert "Desk live files" not in p
    assert "Kīlauea from desk" not in p
    assert "Proposal files" not in p
    assert "Last messages in this chat" not in p
    assert "Already said" in p
    assert "named feature" in p


def test_round_follow_stays_on_ask(monkeypatch):
    monkeypatch.setattr("apps.council.brainstorm.status", lambda: "idle")
    monkeypatch.setattr("apps.council.desk_read.snapshot_for_prompt", lambda **_k: "")
    monkeypatch.setattr("apps.council.desk_read.desk_facts_block", lambda **_k: "")
    p = build_round_follow_prompt(
        voice="bruce",
        prev_voice="ava",
        prev_text="I suggest trivia nights.",
        origin_text="Hey team, lets test a thought session for proposals. Ava, you start...",
    )
    assert "bruce" in p
    assert "thought session" in p
    assert "trivia nights" in p
    assert "English" in p
    assert "Never ping or question yourself" in p
    assert "Do not address Bruce" in p
    assert "Ava and Carly" in p
    assert "@-mention" in p


def test_carly_round_may_curse(monkeypatch):
    monkeypatch.setattr("apps.council.brainstorm.status", lambda: "idle")
    monkeypatch.setattr("apps.council.desk_read.snapshot_for_prompt", lambda **_k: "")
    monkeypatch.setattr("apps.council.desk_read.desk_facts_block", lambda **_k: "")
    p = build_round_follow_prompt(
        voice="carly",
        prev_voice="bruce",
        prev_text="Power looks fine.",
        origin_text="Review the desk bugs.",
    )
    assert "curse at Ava and Bruce" in p
    assert "Do not address Carly" in p
    assert "Ava and Bruce" in p


def test_speak_prompt_forbids_dodge():
    p = build_speak_prompt(
        speaker_line="Speaker: kai",
        display="kai",
        voice="ava",
        user_text="Do you like gardening topics?",
        chat_id=-1,
    )
    assert "Answer their latest question first" in p
    assert "Do not dodge" in p
    assert "philosophy" in p.lower() or "academic" in p.lower()
    assert "security" in p.lower()
    p = build_speak_prompt(
        speaker_line="Speaker: wildecho94",
        display="wildecho",
        voice="ava",
        user_text="hey",
        chat_id=-1,
    )
    assert "Kīlauea" in p or "Kilauea" in p
    assert "live desks" in p.lower() or "desk live" in p.lower()
    assert "<<<JUDGE" not in p
    assert "This is public" in p
    assert "Desk live files" in p
    assert "Desk live files" in p
    assert "NWS Hawaii" in p
    dm = build_speak_prompt(
        speaker_line="Speaker: wildecho94",
        display="wildecho",
        voice="carly",
        user_text="Hi",
        chat_id=1,
        private=True,
    )
    assert "Private Telegram DM" in dm
    assert "Stay in your lane" not in dm
    assert "not a gate" in dm
    assert "<<<JUDGE" in dm
    assert "Greetings, check-ins, and small talk are 0" in dm


def test_speak_prompt_pins_last_three(monkeypatch):
    rows = [
        {"dir": "in", "display": "Kai", "text": "one"},
        {"dir": "out", "voice": "ava", "text": "two"},
        {"dir": "in", "display": "Kai", "text": "three"},
        {"dir": "in", "display": "Kai", "text": "four extra"},
    ]
    monkeypatch.setattr("apps.council.chatlog.recent_for_chat", lambda *_a, **_k: rows)
    p = build_speak_prompt(
        speaker_line="Speaker: Kai",
        display="Kai",
        voice="ava",
        user_text="now",
        chat_id=-1,
        extra="x" * 4000,
    )
    assert "Last messages in this chat" in p
    assert "two" in p
    assert "three" in p
    assert "four extra" in p


def test_carly_persona_may_curse():
    from apps.council.personas import CARLY_SYSTEM

    assert "VOICE LOCK" in CARLY_SYSTEM
    assert "curse and bitch at Ava and Bruce" in CARLY_SYSTEM
    assert "do not curse at humans" in CARLY_SYSTEM.lower()
