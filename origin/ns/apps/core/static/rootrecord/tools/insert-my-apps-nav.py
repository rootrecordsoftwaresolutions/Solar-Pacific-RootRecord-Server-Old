#!/usr/bin/env python3
"""Shim — runtime is ~/.ollama/skills/site-ops/scripts/insert-my-apps-nav.py."""
from pathlib import Path
import runpy
_p = Path.home() / ".ollama" / "skills" / "site-ops" / "scripts" / "insert-my-apps-nav.py"
if not _p.is_file():
    raise FileNotFoundError(_p)
runpy.run_path(str(_p), run_name="__main__")
