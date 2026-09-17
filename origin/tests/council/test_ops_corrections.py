from apps.council.ops_corrections import HINT, add, note_owner, prompt_lines
from apps.council.desk_read import _hurricane_desk_line
from apps.core.services.hurricane_desk import hawaii_is_local_threat, public_payload


def test_dujuan_is_not_hawaii_threat():
    assert hawaii_is_local_threat(2904, None) is False
    assert hawaii_is_local_threat(200, None) is True
    pub = public_payload()
    text = str(pub.get("hawaii") or "")
    assert "not a Hawai" in text or "NOT a Hawai" in text or "not a Hawaiʻi" in text.lower() or "not a hawaii" in text.lower()
    line = _hurricane_desk_line()
    assert "NOT a Hawai" in line or "not a Hawai" in line.lower()


def test_ops_corrections_bind(tmp_path, monkeypatch):
    monkeypatch.setattr("apps.council.ops_corrections.PATH", tmp_path / "ops.json")
    note_owner("It's 3k miles away heading away west into Japan right?")
    blob = prompt_lines()
    assert "Japan" in blob
    add("Accept ops. Do not alarm.", source="test")
    assert "Do not alarm" in prompt_lines()
    assert HINT.search("chill out about the storm")
    note_owner("/status")
    # slash commands are ignored by HINT ingest except OPSFIX
