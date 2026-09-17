#!/usr/bin/env python3
"""Refresh every ecosystem topic skill (CURRENT.md + EcoFlow + history + data maps)."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

if __name__ == "__main__":
    here = Path(__file__).resolve().parent
    py = sys.executable
    subprocess.check_call([py, str(here / "build_catalog.py")])
    subprocess.check_call([py, str(here / "refresh_data_maps.py")])
    subprocess.check_call([py, str(here / "link_desk.py")])
    subprocess.check_call([py, str(here / "bootstrap_ollama_home.py")])
    subprocess.check_call(
        [py, str(here.parents[1] / "fs-index" / "scripts" / "incremental_fs_index.py")]
    )
