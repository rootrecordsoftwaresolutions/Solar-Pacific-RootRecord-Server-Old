#!/usr/bin/env python3
"""Shim — runtime is /home/rootrecord/.ollama/skills/rootmc/scripts/build-feature-graphic.py."""
import os
os.environ.setdefault('ROOTMC_ANDROID_ROOT', '/home/rootrecord/.ollama/skills/origin/workstations/android/rootmc')
from pathlib import Path
import runpy
_p = Path('/home/rootrecord/.ollama/skills/rootmc/scripts/build-feature-graphic.py')
if not _p.is_file():
    raise FileNotFoundError(_p)
runpy.run_path(str(_p), run_name='__main__')
