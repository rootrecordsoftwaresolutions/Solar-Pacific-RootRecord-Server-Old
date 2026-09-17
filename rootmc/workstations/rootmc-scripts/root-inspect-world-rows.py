#!/usr/bin/env python3
"""Shim — runtime is /home/rootrecord/.ollama/skills/rootmc/scripts/ops/root-inspect-world-rows.py."""
from pathlib import Path
import runpy
_p = Path('/home/rootrecord/.ollama/skills/rootmc/scripts/ops/root-inspect-world-rows.py')
if not _p.is_file():
    raise FileNotFoundError(_p)
runpy.run_path(str(_p), run_name='__main__')
