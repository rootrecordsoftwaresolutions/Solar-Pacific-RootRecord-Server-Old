from apps.council import audio_request, commands


def test_parse_voice():
    assert audio_request.parse_voice("Ava") == "ava"
    assert audio_request.parse_voice("bruce please") == "bruce"
    assert audio_request.parse_voice("Carly Mal") == "carly"
    assert audio_request.parse_voice("nope") is None


def test_help_lists_audio():
    text = commands.help_text()
    assert "/audio" in text
    assert "WAV" in text or "voice" in text.lower()
    bruce = commands.help_text(voice="bruce")
    assert "/audio" in bruce
