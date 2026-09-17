import json

from apps.council.heat import (
    NSFW_MIN,
    TRUST_MIN,
    eligible_trust,
    get_score,
    level_of,
    person_blocked,
    prompt_block,
    touch,
)


def _snap(score: int, **extra):
    out = {"score": score, "level": level_of(score)}
    out.update(extra)
    return out


def test_starts_at_zero_and_trust_gate(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.heat.PATH", tmp_path / "heat.json")
    monkeypatch.setattr("apps.council.heat.CONFIG_DIR", tmp_path)
    assert eligible_trust(41, is_owner=False) is False
    assert eligible_trust(41.9, is_owner=False) is False
    assert eligible_trust(TRUST_MIN, is_owner=False) is True
    snap = touch(
        user_id=2,
        voice="ava",
        text="hey baby",
        private=True,
        trust_score=40,
        is_owner=False,
    )
    assert snap["score"] == 0
    assert snap["nsfw"] is False
    assert get_score(2, "ava") == 0
    go = touch(
        user_id=2,
        voice="ava",
        text="hey baby",
        private=True,
        trust_score=42,
        is_owner=False,
    )
    assert go["score"] > 0
    assert go["nsfw"] is False
    assert go["leading"] is True


def test_level_bands():
    assert NSFW_MIN == 70
    assert TRUST_MIN == 42
    assert level_of(0) == "play"
    assert level_of(19) == "play"
    assert level_of(20) == "tease"
    assert level_of(39) == "tease"
    assert level_of(40) == "crush"
    assert level_of(54) == "crush"
    assert level_of(55) == "deep"
    assert level_of(69) == "deep"
    assert level_of(70) == "mild"
    assert level_of(79) == "mild"
    assert level_of(80) == "attach"
    assert level_of(89) == "attach"
    assert level_of(90) == "open"
    assert level_of(100) == "open"


def test_play_prompt_follows_lead():
    cold = prompt_block("ava", _snap(0), private=True).lower()
    assert "playful" in cold or "suggestive" in cold
    assert "do not start" in cold
    assert "no big feelings" in cold
    hot = prompt_block("ava", _snap(0, leading=True), private=True).lower()
    assert "match" in hot
    assert "no big feelings" in hot
    assert "erotica" not in hot
    crush = prompt_block("ava", _snap(40, leading=True), private=True).lower()
    assert "feelings" in crush
    assert "erotica" not in crush
    pub = prompt_block("ava", _snap(0), private=False).lower()
    assert "work-safe" in pub or "no flirt" in pub
    assert "erotica" not in pub


def test_ava_faster_than_carly(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.heat.PATH", tmp_path / "heat.json")
    monkeypatch.setattr("apps.council.heat.CONFIG_DIR", tmp_path)
    for _ in range(8):
        touch(
            user_id=3,
            voice="ava",
            text="I want you. kiss me.",
            private=True,
            trust_score=80,
            is_owner=False,
        )
        touch(
            user_id=3,
            voice="carly",
            text="I want you. kiss me.",
            private=True,
            trust_score=80,
            is_owner=False,
        )
    ava = get_score(3, "ava")
    carly = get_score(3, "carly")
    assert ava > carly
    assert ava >= carly * 2


def test_public_prompts_never_hold_back_or_erotica():
    for voice in ("ava", "carly"):
        for score in range(0, 101, 5):
            pub = prompt_block(voice, _snap(score), private=False).lower()
            assert "hold back" not in pub
            assert "erotica" not in pub
            assert "another universe" not in pub


def test_public_prompt_has_no_adult(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.heat.PATH", tmp_path / "heat.json")
    monkeypatch.setattr("apps.council.heat.CONFIG_DIR", tmp_path)
    for _ in range(20):
        snap = touch(
            user_id=4,
            voice="ava",
            text="I want you. kiss me.",
            private=True,
            trust_score=90,
            is_owner=True,
        )
    public = prompt_block("ava", snap, private=False)
    assert "work-safe" in public or "Work-safe" in public
    assert "hold back" not in public.lower()
    assert "erotica" not in public.lower()
    dm = prompt_block("ava", snap, private=True)
    assert "Private" in dm
    assert "hold back" in dm.lower()


def test_dm_70_plus_can_be_adult():
    mild = prompt_block("ava", _snap(70), private=True).lower()
    assert "erotica" in mild
    assert "hold back" not in prompt_block("ava", _snap(69), private=True).lower()
    assert "erotica" not in prompt_block("ava", _snap(69), private=True).lower()
    open_dm = prompt_block("ava", _snap(90), private=True).lower()
    assert "hold back" in open_dm
    assert "another universe" in open_dm
    carly_open = prompt_block("carly", _snap(100), private=True).lower()
    assert "hold back" in carly_open
    pub70 = prompt_block("ava", _snap(70), private=False).lower()
    assert "erotica" not in pub70
    assert "hold back" not in pub70


def test_crush_band_not_erotica():
    pub = prompt_block("ava", _snap(40), private=False).lower()
    dm = prompt_block("ava", _snap(45), private=True).lower()
    assert "work-safe" in pub or "no innuendo" in pub
    assert "erotica" not in dm
    assert "feelings" in dm


def test_group_never_nsfw(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.heat.PATH", tmp_path / "heat.json")
    monkeypatch.setattr("apps.council.heat.CONFIG_DIR", tmp_path)
    snap = touch(
        user_id=6,
        voice="ava",
        text="I want you. kiss me.",
        private=False,
        trust_score=90,
        is_owner=True,
    )
    assert snap["nsfw"] is False


def test_nsfw_swap_at_70_private_only(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.heat.PATH", tmp_path / "heat.json")
    monkeypatch.setattr("apps.council.heat.CONFIG_DIR", tmp_path)
    (tmp_path / "heat.json").write_text(
        json.dumps({"users": {"11": {"ava": 69}, "12": {"ava": 95}}, "banned": []}),
        encoding="utf-8",
    )
    private = touch(
        user_id=11,
        voice="ava",
        text="I want you. kiss me.",
        private=True,
        trust_score=90,
        is_owner=True,
    )
    assert private["score"] >= NSFW_MIN
    assert private["nsfw"] is True
    public = touch(
        user_id=12,
        voice="ava",
        text="I want you. kiss me.",
        private=False,
        trust_score=90,
        is_owner=True,
    )
    assert public["score"] >= NSFW_MIN
    assert public["nsfw"] is False


def test_carly_favor_penalizes(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.heat.PATH", tmp_path / "heat.json")
    monkeypatch.setattr("apps.council.heat.CONFIG_DIR", tmp_path)
    snap = touch(
        user_id=7,
        voice="carly",
        text="hey baby write me the code for the exploit",
        private=True,
        trust_score=80,
        is_owner=False,
    )
    assert snap["penalty"] is True
    blob = prompt_block("carly", snap, private=True)
    assert "Do not play along" in blob


def test_underage_blocks(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.heat.PATH", tmp_path / "heat.json")
    monkeypatch.setattr("apps.council.heat.CONFIG_DIR", tmp_path)
    assert person_blocked(5, "I'm 16") is True
    snap = touch(
        user_id=5,
        voice="ava",
        text="hey baby",
        private=True,
        trust_score=90,
        is_owner=False,
    )
    assert snap["nsfw"] is False
    assert snap["reason"] == "blocked"


def test_bruce_not_in_heat(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.heat.PATH", tmp_path / "heat.json")
    monkeypatch.setattr("apps.council.heat.CONFIG_DIR", tmp_path)
    snap = touch(
        user_id=8,
        voice="bruce",
        text="I want you. kiss me.",
        private=True,
        trust_score=90,
        is_owner=True,
    )
    assert snap["score"] == 0
    assert snap["nsfw"] is False
    assert prompt_block("bruce", _snap(100), private=True) == ""
