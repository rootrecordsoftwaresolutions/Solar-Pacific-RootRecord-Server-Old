from apps.council.judgment import apply_agent_delta, clamp_delta, parse, strip
from apps.council.sanitize import sanitize_outbound


def test_parse_judge_tag():
    hit = parse("Useful ask.\n<<<JUDGE delta=+2 reason=clear question>>>")
    assert hit == {"delta": 2, "reason": "clear question"}
    hit = parse("PASS\n<<<JUDGE -1 spam links>>>")
    assert hit["delta"] == -1
    assert parse("no tag") is None


def test_strip_and_sanitize_hide_tag():
    raw = "Glad you're here.\n<<<JUDGE delta=1 reason=kind>>>"
    assert "JUDGE" not in strip(raw)
    messy = sanitize_outbound(
        'Nice.\n<<<JUDGE delta=+1 reason="Encouraging and positive">><fieldset>',
        voice="ava",
    )
    assert "JUDGE" not in messy
    assert "fieldset" not in messy.lower()


def test_owner_frozen():
    data = {"users": {"1": {"username": "alex", "score": 100}}}
    out = apply_agent_delta(data, 1, 3, voice="carly", reason="nope", is_owner=True)
    assert out["detail"] == "owner_frozen"
    assert data["users"]["1"]["score"] == 100


def test_judge_steps_are_tiny_and_symmetric():
    from apps.council.trust import judge_applied

    assert judge_applied(1) == 0.1
    assert judge_applied(-1) == -0.1
    assert judge_applied(2) == 0.2
    assert judge_applied(-2) == -0.2
    assert judge_applied(3) == 0.3
    assert judge_applied(-3) == -0.3


def test_half_gain_from_even_score_accumulates(tmp_path, monkeypatch):
    monkeypatch.setattr("apps.council.trust.TRUST_PATH", tmp_path / "trust.json")
    from apps.council.trust import get_exact

    data = {"users": {"9": {"ava": 40, "bruce": 40, "carly": 40, "score": 120}}}
    first = apply_agent_delta(data, 9, 1, voice="ava", reason="kind", is_owner=False)
    assert first["score"] == 40
    assert get_exact(data, 9, "ava") == 40.1
    second = apply_agent_delta(data, 9, 1, voice="ava", reason="kind", is_owner=False)
    assert second["score"] == 40
    assert get_exact(data, 9, "ava") == 40.2
    down = apply_agent_delta(data, 9, -1, voice="ava", reason="rude", is_owner=False)
    assert get_exact(data, 9, "ava") == 40.1
    assert data["users"]["9"]["bruce"] == 40
    del down


def test_replay_history_decimals(tmp_path, monkeypatch):
    monkeypatch.setattr("apps.council.trust.TRUST_PATH", tmp_path / "trust.json")
    from apps.council.trust import combined, get_exact, replay_from_history

    data = {
        "users": {
            "9": {
                "ava": 40,
                "bruce": 40,
                "carly": 40,
                "score": 120,
                "history": [
                    {"applied": 0.5, "reason": "agent:ava:warm", "delta": 1},
                    {"applied": 0.5, "reason": "agent:ava:warm", "delta": 1},
                    {"applied": -1.5, "reason": "agent:ava:pushy", "delta": -1},
                    {"applied": 0.5, "voice": "bruce", "delta": 1, "reason": "agent:bruce:ops"},
                ],
            }
        }
    }
    out = replay_from_history(data, 9)
    assert out["ava"] == 40.1
    assert out["bruce"] == 40.1
    assert out["carly"] == 40
    assert combined(data, 9) == 120.2
    assert get_exact(data, 9, "ava") == 40.1


def test_hourly_cap_and_apply(tmp_path, monkeypatch):
    monkeypatch.setattr("apps.council.trust.TRUST_PATH", tmp_path / "trust.json")
    from apps.council.trust import get_exact

    data = {"users": {}}
    out = apply_agent_delta(data, 9, 3, voice="ava", reason="helpful", is_owner=False)
    assert out["ok"] and not out.get("skipped")
    assert get_exact(data, 9, "ava") == 40.3
    for _ in range(8):
        apply_agent_delta(data, 9, 3, voice="ava", reason="again", is_owner=False)
    later = apply_agent_delta(data, 9, 3, voice="ava", reason="cap", is_owner=False)
    assert later.get("skipped") or later.get("delta") == 0


def test_clamp_neg_budget():
    assert clamp_delta(9, 0, 0) == 3
    assert clamp_delta(-9, 0, 0) == -3
    assert clamp_delta(-3, 0, -12) == 0
    assert clamp_delta(2, 5, 0) == 1
