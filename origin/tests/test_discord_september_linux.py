import importlib.util
import os
from pathlib import Path


def _load():
    path = Path.home() / ".ollama" / "skills" / "discord" / "scripts" / "discord_september.py"
    spec = importlib.util.spec_from_file_location("discord_september", path)
    return spec.loader.load_module()


def test_pid_alive_self_and_missing():
    mod = _load()
    assert mod._pid_alive(os.getpid()) is True
    assert mod._pid_alive(0) is False
    assert mod._pid_alive(99999999) is False


def test_nuke_refuses_without_manifest(tmp_path, monkeypatch):
    mod = _load()
    monkeypatch.setattr(mod, "MANIFEST", tmp_path / "MANIFEST.json")
    try:
        import asyncio

        asyncio.run(mod.nuke())
        raise AssertionError("nuke should refuse")
    except SystemExit as e:
        assert "archive first" in str(e)
