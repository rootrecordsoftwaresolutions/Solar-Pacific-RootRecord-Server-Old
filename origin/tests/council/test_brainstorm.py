from apps.council.brainstorm import (
    MIN_S,
    parse_duration_s,
    parse_topic,
    _ask_prompt,
    _looks_start,
    _looks_stop,
)


def test_min_ten_minutes():
    dur, bumped = parse_duration_s("5 minutes")
    assert dur == MIN_S and bumped is True
    dur, bumped = parse_duration_s("30 min")
    assert dur == 30 * 60 and bumped is False
    dur, bumped = parse_duration_s("2 hours")
    assert dur == 2 * 3600 and bumped is False
    dur, bumped = parse_duration_s("/brainstorm 45")
    assert dur == 45 * 60
    assert parse_duration_s("hello") is None


def test_parse_topic():
    assert parse_topic("/brainstorm") == ""
    assert parse_topic("/brainstorm@avaivy_bot generator badge") == "generator badge"
    assert parse_topic("/brainstorm 20 generator card") == "generator card"
    assert parse_topic("the generator for 30 minutes") == "the generator"
    assert parse_topic("30 minutes") == ""


def test_origin_continue_asks_for_pass_not_weather():
    from apps.council.brainstorm import _origin_continue

    round_txt = _origin_continue(wrap=False, topic="NPU soak")
    wrap_txt = _origin_continue(wrap=True, topic="NPU soak")
    assert round_txt.startswith("Timed brainstorm")
    assert "Stay on this topic only: NPU soak" in round_txt
    assert round_txt.index("Stay on this topic only") < round_txt.index("Do not /done")
    assert "PASS" in round_txt
    assert "ecoflow-live" not in round_txt.lower()
    assert "AMEND the open daily plan in place" not in round_txt
    assert "Do not start new topics" in wrap_txt
    assert "NPU soak" in wrap_txt
    assert "left the topic" in wrap_txt
    both = _ask_prompt(have_topic=False, have_duration=False)
    assert "What should we think about?" in both
    assert "How long?" in both
    topic_only = _ask_prompt(have_topic=True, have_duration=False)
    assert "What should we think about?" not in topic_only
    assert "How long?" in topic_only


def test_start_and_stop_phrases():
    assert _looks_start("/brainstorm")
    assert _looks_start("let's brainstorm")
    assert _looks_start("thought session")
    assert _looks_stop("stop")
    assert _looks_stop("wrap up")
    assert not _looks_stop("stop talking")


def test_round_meta_files_only_on_wrap():
    from apps.council.brainstorm import _round_meta

    mid = _round_meta(wrap=False, pid="plan-x")
    assert mid["allow_pass"] is True
    assert mid["no_propose"] is True
    assert mid["brainstorm_wrap"] is False
    wrap = _round_meta(wrap=True, pid="plan-x")
    assert wrap["brainstorm_wrap"] is True
    assert wrap["no_propose"] is False
    assert wrap["allow_pass"] is True


def test_origin_rotates_round_jobs():
    from apps.council.brainstorm import _origin_continue

    first = _origin_continue(wrap=False, topic="ops badge", n=1)
    second = _origin_continue(wrap=False, topic="ops badge", n=2)
    wrap = _origin_continue(wrap=True, topic="ops badge", n=4)
    assert "visitor-facing" in first
    assert "new option" in second
    assert first != second
    assert "three bullets" in wrap
    assert "Bruce files only at wrap" in first


def test_note_said_skips_pass_and_repeats(tmp_path, monkeypatch):
    import json

    from apps.council import brainstorm as bs

    path = tmp_path / "brainstorm.json"
    path.write_text(json.dumps({"status": "running", "topic": "badge", "said": []}), encoding="utf-8")
    monkeypatch.setattr(bs, "PATH", path)
    bs.note_said("ava", "Show generator state as a badge on Ava Ops.")
    bs.note_said("bruce", "PASS")
    bs.note_said("ava", "Show generator state as a badge on Ava Ops.")
    bs.note_said("carly", "Refuse live watts on that badge unless the desk file is fresh.")
    block = bs.claimed_block()
    assert "badge on Ava Ops" in block
    assert "Refuse live watts" in block
    assert "- bruce:" not in block.lower()
    assert "PASS if you have nothing new" in block
    assert block.lower().count("ava:") == 1
