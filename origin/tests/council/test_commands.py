import re

from apps.council import commands


def test_normalize_slash_strips_bot_suffix():
    assert commands.normalize_slash("/status@AvaCouncilBot") == "/status"
    assert commands.normalize_slash("/approve@AvaCouncilBot plan-abc123") == "/approve plan-abc123"
    assert commands.normalize_slash("/discussion@bot on") == "/discussion on"
    assert commands.normalize_slash("status") == "status"
    assert commands.normalize_slash("/ping") == "/ping"


def test_help_says_ava_menu_and_dm_split():
    text = commands.help_text()
    assert "Ava" in text
    assert "/brainstorm" in text
    bruce = commands.help_text(voice="bruce")
    assert "/ping" in bruce
    assert "/brainstorm" not in bruce
    assert "/goals" in bruce
    assert "Bruce" in bruce
    carly = commands.help_text(voice="carly")
    assert "Carly" in carly
    assert "/approve" not in carly


def test_approve_regex_after_normalize():
    pat = re.compile(r"^/(approve|reject|implement)(?:\s+(\S+))?$")
    low = commands.normalize_slash("/approve@SomeBot plan-f7bd8b48").lower()
    m = pat.match(low)
    assert m is not None
    assert m.group(1) == "approve"
    assert m.group(2) == "plan-f7bd8b48"
    assert pat.match(commands.normalize_slash("/approve@SomeBot").lower())
