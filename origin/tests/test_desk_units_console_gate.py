from pathlib import Path


def test_user_units_require_console_up_flag():
    user = Path.home() / ".config" / "systemd" / "user"
    needle = "ConditionPathExists=%h/.ollama/skills/state/store/ava-console-up"
    for name in (
        "ava-ecoflow-ble.service",
        "ava-hybrid-night.service",
        "ava-council.service",
        "ava-bt-bridge.service",
        "ava-auto-push.service",
    ):
        text = (user / name).read_text(encoding="utf-8")
        assert needle in text, name
        assert "Restart=always" not in text, name
    flm = (user / "ava-flm.service").read_text(encoding="utf-8")
    assert needle in flm
    assert "Restart=always" not in flm
    assert "WantedBy=graphical-session.target" not in flm
    assert "--pmode default" in flm
