#!/usr/bin/env python3
"""Direct entrypoint for systemd when python -m apps.council is awkward.

ExecStart example:
  /home/rootrecord/.ollama/skills/origin/.venv/bin/python \\
    /home/rootrecord/.ollama/skills/origin/apps/council/run.py
"""
from __future__ import annotations

import runpy
import sys
from pathlib import Path

# Ensure Ava-Core root on path so `apps.council` imports resolve when needed
ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

if __name__ == "__main__":
    # Prefer package main
    from apps.council.__main__ import main

    raise SystemExit(main())
