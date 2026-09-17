#!/usr/bin/env python3
"""Allowlisted public GET."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "web-facts" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from web_facts import main as web_main  # noqa: E402

if __name__ == "__main__":
    raise SystemExit(web_main())
