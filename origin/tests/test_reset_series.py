import importlib.util
from pathlib import Path


def _load():
    path = Path.home() / ".ollama" / "skills" / "host-metrics" / "scripts" / "reset_series.py"
    spec = importlib.util.spec_from_file_location("reset_series", path)
    return spec.loader.load_module()


def test_reset_series_dry_run_lists_jsonl():
    mod = _load()
    rows = mod.reset(dry_run=True)
    assert isinstance(rows, list)
    for line in rows:
        assert "->" in line
        assert "jsonl" in line
