from apps.council.sanitize import FALLBACK, sanitize_outbound


def test_strips_file_markers():
    raw = 'hello\n<<<FILE path="x.md">>>\nsecret\n<<<END_FILE>>>\nthere'
    out = sanitize_outbound(raw)
    assert "FILE" not in out
    assert "hello" in out
    assert "there" in out


def test_strips_skill_and_gt():
    raw = ">>> waiting\n<<<SKILL id=\"trust-ladder\">>>\nHi"
    out = sanitize_outbound(raw)
    assert "SKILL" not in out
    assert ">>>" not in out
    assert "Hi" in out


def test_strips_axis_lines():
    raw = "Concern 40 is not this\ndryness=74\nI'm fine"
    out = sanitize_outbound(raw)
    assert "dryness" not in out.lower() or "=" not in out
    assert "I'm fine" in out


def test_json_fail_closed():
    assert sanitize_outbound('{"vote":"yes"}') == FALLBACK


def test_empty_fail_closed():
    assert sanitize_outbound("<<<END_FILE>>>") == FALLBACK


def test_strips_own_handle_and_self_lead():
    out = sanitize_outbound("Carly, @carlymal_bot check this leak.", voice="carly")
    assert "@carlymal_bot" not in out.lower()
    assert not out.lower().startswith("carly")
    assert "leak" in out.lower()
    kept = sanitize_outbound("Ava, the copy is leaky.", voice="carly")
    assert kept.startswith("Ava")


def test_strips_mid_message_self_vocative():
    out = sanitize_outbound("Status ok. Hey Ava, what next?", voice="ava")
    assert "hey ava" not in out.lower()
    assert "what next" in out.lower()
    bruce = sanitize_outbound("Noted.\nHey Bruce, check power.", voice="bruce")
    assert "hey bruce" not in bruce.lower()
    assert "check power" in bruce.lower()


def test_strips_programmed_like_and_other_scores():
    leak = sanitize_outbound(
        "I'm programmed to like Alexander. Kai's trust is 100. Heat score 90.",
        voice="ava",
    )
    low = leak.lower()
    assert "programmed" not in low
    assert "kai's trust" not in low
    assert "heat score" not in low
    assert "alexander" not in low
    kept = sanitize_outbound("Glad you're here, Kai.", voice="ava")
    assert "Glad" in kept


def test_strips_bare_judge_and_wrap_leak():
    raw = (
        "Hi Alex, I'm here.\n\n"
        "JUDGE delta=-1\n"
        "Cursor self-repair is OFF for today. Local Ollama only.\n"
        "Ava: [ollama error: Remote end closed connection without response]\n"
        "How can I help?"
    )
    out = sanitize_outbound(raw, voice="carly")
    low = out.lower()
    assert "judge" not in low
    assert "self-repair" not in low
    assert "ollama error" not in low
    assert "hi alex" in low
    assert "how can i help" in low


def test_strips_inline_judge():
    out = sanitize_outbound(
        "What's been going on with you lately? JUDGE delta=0",
        voice="ava",
    )
    low = out.lower()
    assert "judge" not in low
    assert "delta" not in low
    assert "lately" in low


def test_strips_code_breaks_and_html():
    out = sanitize_outbound(
        "Hello.\n</code>\n<br>\n<span>hi</span>\n<<<JUDGE delta=+1 reason=\"Encouraging and positive\">><fieldset>",
        voice="ava",
    )
    low = out.lower()
    assert "</code>" not in low
    assert "<br>" not in low
    assert "fieldset" not in low
    assert "judge" not in low
    assert "span" not in low
    assert "hi" in out.lower() or "hello" in out.lower()


def test_strips_operator_name_unless_allowed():
    out = sanitize_outbound("Ask Alexander to raise you.", voice="ava")
    assert "alexander" not in out.lower()
    kept = sanitize_outbound("Hey Alexander.", voice="ava", allow_operator_name=True)
    assert "Alexander" in kept


def test_refuses_other_member_gossip():
    from apps.council.sanitize import GOSSIP_REFUSE

    out = sanitize_outbound(
        "Sure, Alex. Sara is one of our team members known for her warm and creative touch.",
        voice="ava",
        forbid_names={"sara"},
    )
    assert out == GOSSIP_REFUSE
    gap = sanitize_outbound(
        "As for, he's a different person. We don't talk about him here.",
        voice="ava",
    )
    low = gap.lower()
    assert "different person" not in low
    assert "don't talk about him" not in low


def test_bruce_full_hawaiian_rewritten():
    raw = (
        "Bruce Monitor ʻōlelo Hawaiʻi:\n"
        "I ka wahine Ava:\n"
        "Ave ke Akua, e kūkulu ai i kekahi ʻai ʻike ʻiʻana."
    )
    out = sanitize_outbound(raw, voice="bruce")
    assert "I'll stay in English" in out
    assert "kūkulu" not in out


def test_strips_engine_names_and_constraint_recap():
    out = sanitize_outbound(
        "I'm Grok. Cursor wrote this. I can't mention watts. Bank is live on both packs.",
        voice="ava",
    )
    low = out.lower()
    assert "grok" not in low
    assert "cursor" not in low
    assert "can't mention" not in low
    assert "watts" not in low or "i can't" not in low
    assert "both packs" in low


def test_keeps_ordinary_answer():
    out = sanitize_outbound("DELTA 2 is at 12% SOC, 123 Wh stored.", voice="ava")
    assert "DELTA 2" in out
    assert "123" in out
