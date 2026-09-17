from apps.council.telegram import split_chunks


def test_split_chunks_keeps_short():
    assert split_chunks("hello") == ["hello"]


def test_split_chunks_breaks_on_paragraph():
    a = "A" * 40
    b = "B" * 40
    parts = split_chunks(a + "\n\n" + b, cap=50)
    assert len(parts) >= 2
    assert "A" in parts[0]
    assert "B" in parts[-1]
    assert all(len(p) <= 50 for p in parts)
