from pathlib import Path

SKILLS = Path(__file__).resolve().parents[1] / ".cursor" / "skills"

REQUIRED = (
    "scheduler-clock",
    "boot-idle-origin",
    "reports-voice",
    "weather-kilauea",
    "ecoflow-automations",
    "council-telegram",
    "ava-ops",
    "rootmc",
    "public-edge",
    "media-hybrid",
    "ecosystem-history",
    "desk-data-reader",
    "live-directories",
    "ecosystem-index",
)


def test_every_topic_skill_is_a_desk():
    for name in REQUIRED:
        skill = SKILLS / name
        assert (skill / "SKILL.md").is_file(), name
        assert (skill / "INDEX.md").is_file(), name
        assert (skill / "desk" / "README.md").is_file(), name
        links = list((skill / "desk").rglob("*"))
        assert any(p.is_symlink() for p in links), name


def test_desk_read_skill_alias_not_blocked():
    from apps.council.desk_read import read_spec, resolve

    assert resolve(".cursor/skills/weather-kilauea/INDEX.md") is not None
    body = read_spec("skill:weather-kilauea")
    assert body != "blocked path"
