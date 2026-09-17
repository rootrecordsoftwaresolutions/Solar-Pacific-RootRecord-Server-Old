from apps.council.skills import inject_blocks, match_exec_skill, match_read_skills
from apps.council.skills.hawaiian_norm import fold_hawaiian
from apps.council.sanitize import sanitize_outbound


def test_kiweh_injects_glossary():
    text = "@carlymal_bot what is Kiweh vs Kileau"
    ids = match_read_skills(text)
    assert "kilauea-glossary" in ids
    assert "hawaiian-glossary" not in ids
    block = inject_blocks(ids, text=text)
    assert "Kīlauea" in block or "Kilauea" in block or "kīlauea" in block.lower()
    assert "invent" in block.lower() or "misspell" in block.lower() or "Kiweh" in block
    assert "not hawaiian" in block.lower() or "Kiweh" in block


def test_exec_not_auto_matched_as_read():
    ids = match_read_skills("run status script please")
    assert "council-status" not in ids
    meta = match_exec_skill("run the status script")
    assert meta and meta["id"] == "council-status"


def test_skill_tokens_never_leave_sanitizer():
    assert "SKILL" not in sanitize_outbound('<<<SKILL id="kilauea-glossary">>> facts')


def test_strips_own_bot_handle():
    assert "@avaivy_bot" not in sanitize_outbound("Ava @avaivy_bot what now", voice="ava")
    assert "@brucemonitor_bot" in sanitize_outbound("ask @brucemonitor_bot", voice="ava")


def test_aina_and_ascii_hit_hawaiian_glossary():
    for text in ("what is ʻāina", "what is aina"):
        ids = match_read_skills(text)
        assert "hawaiian-glossary" in ids
        block = inject_blocks(ids, text=text)
        assert "āina" in block.lower() or "aina" in block.lower()


def test_aloha_and_mahalo_match():
    assert "hawaiian-glossary" in match_read_skills("Aloha everyone")
    assert "hawaiian-glossary" in match_read_skills("mahalo for that")


def test_kilauea_macron_equals_ascii():
    assert fold_hawaiian("Kīlauea") == fold_hawaiian("kilauea")
    with_macron = match_read_skills("Tell me about Kīlauea")
    ascii_form = match_read_skills("Tell me about kilauea")
    assert "kilauea-glossary" in with_macron
    assert "kilauea-glossary" in ascii_form
    assert "hawaiian-glossary" not in with_macron
    assert "hawaiian-glossary" not in ascii_form


def test_plain_chat_does_not_inject_hawaiian():
    ids = match_read_skills("queue looks fine, thanks")
    assert "hawaiian-glossary" not in ids


def test_clock_phrase_does_not_inject_hawaiian():
    text = "Weather sample 14:06 Hawaiian Standard Time. Hawaii Pacific Solar Root Server."
    assert "hawaiian-glossary" not in match_read_skills(text)
    assert "hawaiian-glossary" not in match_read_skills(text, voice="bruce")
    from apps.council.skills.hawaiian_inject import format_hawaiian_block

    block = format_hawaiian_block(text)
    assert "Desk:" not in block
    assert "the islands" not in block.lower()


def test_aloha_with_clock_does_not_add_hawaii_place_desk():
    from apps.council.skills.hawaiian_inject import format_hawaiian_block

    block = format_hawaiian_block("aloha — 14:06 Hawaiian Standard Time")
    assert "aloha:" in block.lower()
    assert "the islands" not in block.lower()


def test_dict_lookup_when_hawaiian_word_appears():
    text = "what does hoʻoponopono mean"
    ids = match_read_skills(text)
    assert "hawaiian-glossary" in ids
    block = inject_blocks(ids, text=text)
    assert "hoʻoponopono" in block.lower() or "hooponopono" in fold_hawaiian(block)
