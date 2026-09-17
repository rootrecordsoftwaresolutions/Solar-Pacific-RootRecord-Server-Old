from pathlib import Path

from apps.council.desk_read import resolve


def test_ava_core_is_not_ollama_home():
    import sys

    sys.path.insert(
        0,
        str(Path.home() / "RootRecord" / "Ava-Core" / ".cursor" / "skills" / "ecosystem-index" / "scripts"),
    )
    import rr_home

    assert (rr_home.AVA / "pyproject.toml").is_file()
    assert rr_home.AVA.name == "Ava-Core"
    assert rr_home.OLLAMA_SKILLS == Path.home() / ".ollama" / "skills"


def test_cursor_skills_symlink_or_dir():
    p = Path.home() / "RootRecord" / "Ava-Core" / ".cursor" / "skills"
    assert p.is_dir()
    assert (p / "ecosystem-index" / "SKILL.md").is_file()


def test_desk_read_allows_cursor_skills_prefix():
    path = resolve(".cursor/skills/ecosystem-index/SKILL.md")
    assert path is not None
    assert path.is_file()
