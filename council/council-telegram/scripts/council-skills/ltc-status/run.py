#!/usr/bin/env python3
"""Litecoin pending + CLI main wallet + equal council vote. No key dumps."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "ltc-node" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from pending_balance import main as pending_main  # noqa: E402

if __name__ == "__main__":
    raise SystemExit(pending_main())
