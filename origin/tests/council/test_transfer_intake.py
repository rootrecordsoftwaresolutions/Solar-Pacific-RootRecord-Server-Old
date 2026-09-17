from apps.council.name_intake import voices_in
from apps.council.transfer import caption_with_hash, sha256_file


def test_name_intake_hears_all_three():
    assert voices_in("Right Ava?") == ["ava"]
    hit = voices_in("ask Bruce and Carly")
    assert "bruce" in hit and "carly" in hit


def test_caption_hash_stays_short(tmp_path):
    p = tmp_path / "note.md"
    p.write_text("hello", encoding="utf-8")
    digest = sha256_file(p)
    cap = caption_with_hash("Proposal plan-x", digest)
    assert "sha256=" in cap
    assert "Proposal" in cap
    assert len(cap) < 900
