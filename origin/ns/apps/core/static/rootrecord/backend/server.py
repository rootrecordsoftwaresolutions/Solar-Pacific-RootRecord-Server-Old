#!/usr/bin/env python3
"""Shim — runtime is ~/.ollama/skills/public-edge/scripts/rootrecord-static-server.py."""
from pathlib import Path
import runpy
_p = Path.home() / ".ollama" / "skills" / "public-edge" / "scripts" / "rootrecord-static-server.py"
if not _p.is_file():
    raise FileNotFoundError(_p)
runpy.run_path(str(_p), run_name="__main__")
