from apps.council.people import apply_tags, observe, prompt_block, strip_tags
from apps.council.sanitize import sanitize_outbound


def test_its_alex_sets_name(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.people.PATH", tmp_path / "people.json")
    monkeypatch.setattr("apps.council.people.CONFIG_DIR", tmp_path)
    observe(99, "It's Alex", username="rootrecordadmin", display_name="rootrecordadmin")
    blob = prompt_block(99)
    assert "Alex" in blob
    assert "Trust " not in blob
    assert "programmed" not in blob.lower()
    from apps.council.people import dossier

    assert dossier(99).get("display_name") == "Alex"


def test_mark_owner_not_handle(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.people.PATH", tmp_path / "people.json")
    monkeypatch.setattr("apps.council.people.CONFIG_DIR", tmp_path)
    from apps.council.people import mark_owner

    mark_owner(7, username="rootrecordadmin")
    blob = prompt_block(7)
    assert "Person: Alexander" in blob
    assert "operator" in blob.lower()
    assert "programmed" not in blob.lower()
    assert "Person: Alexander (@" not in blob


def test_observe_island_and_name(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.people.PATH", tmp_path / "people.json")
    monkeypatch.setattr("apps.council.people.CONFIG_DIR", tmp_path)
    observe(11, "Call me Kai. I live in Puna.", username="kai_hi", display_name="")
    blob = prompt_block(11)
    assert "Kai" in blob
    assert "Puna" in blob or "puna" in blob.lower()
    assert "Person file" in blob


def test_agent_tags_shared(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.people.PATH", tmp_path / "people.json")
    monkeypatch.setattr("apps.council.people.CONFIG_DIR", tmp_path)
    raw = (
        "Got it.\n"
        "<<<NOTE prefers short solar answers>>>\n"
        "<<<FEATURE interest=RootMC>>>\n"
        "<<<FEATURE tone=casual>>>\n"
        "<<<JUDGE delta=1 reason=clear>>>"
    )
    out = apply_tags(raw, 22, voice="bruce")
    assert out.get("skipped") is False
    blob = prompt_block(22)
    assert "RootMC" in blob
    assert "short solar" in blob
    assert "casual" in blob
    assert "NOTE" not in sanitize_outbound(raw, voice="bruce")
    assert "FEATURE" not in sanitize_outbound(raw, voice="ava")
    assert strip_tags(raw).startswith("Got it")


def test_typo_im_lonely_not_a_name(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.people.PATH", tmp_path / "people.json")
    monkeypatch.setattr("apps.council.people.CONFIG_DIR", tmp_path)
    observe(44, "I'm loanly", username="WildEcho94", display_name="WildEcho94")
    from apps.council.people import dossier

    assert dossier(44).get("display_name") != "loanly"
    observe(44, "My name isn't loanly lmao", username="WildEcho94")
    row = dossier(44)
    assert row.get("display_name") != "loanly"
    assert "loanly" not in [str(a).lower() for a in (row.get("aliases") or [])]
    blob = prompt_block(44)
    assert "loanly" not in blob.lower()


def test_secret_not_stored(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.people.PATH", tmp_path / "people.json")
    monkeypatch.setattr("apps.council.people.CONFIG_DIR", tmp_path)
    observe(33, "here is my api_key abcdef", username="x")
    apply_tags("<<<NOTE api_key leaked>>>", 33, voice="carly")
    blob = prompt_block(33)
    assert "abcdef" not in blob
    assert "api_key leaked" not in blob


def test_im_not_ava_drops_agent_name(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.people.PATH", tmp_path / "people.json")
    monkeypatch.setattr("apps.council.people.CONFIG_DIR", tmp_path)
    observe(7, "Call me Alex", username="rootrecordadmin", display_name="Alexander")
    observe(7, "I'm not Ava... You're ava", username="rootrecordadmin", display_name="Alexander")
    from apps.council.people import dossier

    row = dossier(7)
    assert row.get("display_name") != "Ava"
    assert "ava" not in [str(a).lower() for a in (row.get("aliases") or [])]


def test_im_glad_not_a_name(monkeypatch, tmp_path):
    monkeypatch.setattr("apps.council.people.PATH", tmp_path / "people.json")
    monkeypatch.setattr("apps.council.people.CONFIG_DIR", tmp_path)
    observe(8, "I'm glad you stopped", username="x", display_name="x")
    from apps.council.people import dossier

    row = dossier(8)
    assert "glad" not in [str(a).lower() for a in (row.get("aliases") or [])]
    assert row.get("display_name") != "glad"
