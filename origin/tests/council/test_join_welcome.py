from apps.council.join_welcome import announce, greet_lines
from apps.council.trust import DEFAULT_SCORE, band_label


def test_per_voice_trust_and_combined(tmp_path, monkeypatch):
    monkeypatch.setattr("apps.council.trust.TRUST_PATH", tmp_path / "trust.json")
    from apps.council.trust import (
        apply_delta,
        combined,
        format_user_card,
        get_score,
        get_voice,
        load_trust,
        save_trust,
    )

    data = {"users": {"5": {"username": "kai", "score": 40}}}
    save_trust(data, tmp_path / "trust.json")
    data = load_trust(tmp_path / "trust.json")
    assert get_score(data, 5) == 40
    assert combined(data, 5) == 120
    assert get_voice(data, 5, "ava") == 40
    apply_delta(data, 5, 2, voice="ava", gain_factor=1, loss_factor=1)
    assert get_voice(data, 5, "ava") == 42
    assert get_voice(data, 5, "bruce") == 40
    assert combined(data, 5) == 122
    card = format_user_card(data, 5, who="Kai")
    assert "🌸" in card and "🔧" in card and "🛡️" in card
    assert "122" in card
    assert "Combined" in card
    from apps.council.trust import format_card

    card = format_card(who="Kai", score=40)
    assert "you=" not in card
    assert "trust=" not in card
    assert "🌱" in card
    assert "40" in card
    assert "Member" in card
    bumped = format_card(who="Kai", score=42, previous=38)
    assert "38" in bumped and "42" in bumped
    assert "📈" in bumped or "🎉" in bumped
    assert band_label(0) == "Watched"
    assert band_label(24) == "Watched"
    assert band_label(25) == "Guest"
    assert band_label(40) == "Member"
    assert band_label(60) == "Trusted"
    assert band_label(75) == "Core"
    assert band_label(90) == "Peer"
    assert band_label(100) == "Owner"


def test_new_member_all_three_welcome_no_scores():
    lines = greet_lines(name="Kai", score=DEFAULT_SCORE, username="kai_hi")
    voices = [v for v, _ in lines]
    assert voices == ["ava", "bruce", "carly"]
    blob = " ".join(t for _, t in lines)
    assert "Kai" in blob
    assert "40" not in blob
    assert "Member" not in blob
    assert "trust" not in blob.lower()
    assert "secret" in blob.lower() or "tokens" in blob.lower()
    assert "alexander" not in blob.lower()


def test_owner_join_not_lectured_as_forty():
    lines = greet_lines(name="Alexander", score=100, username="Alexrs94")
    blob = " ".join(t for _, t in lines)
    assert "Owner" in blob
    assert "start as Member" not in blob


def test_announce_posts_all_voices():
    posted: list[tuple[str, str]] = []
    out = announce(
        None,
        -1,
        name="Sara",
        score=40,
        username="sara",
        poster=lambda v, t: posted.append((v, t)) or {"ok": True},
    )
    assert out["sent"] == 3
    assert [v for v, _ in posted] == ["ava", "bruce", "carly"]
    assert "Sara" in posted[0][1]
