#!/usr/bin/env python3
"""Storm vs Hawaiʻi distance/bearing. File facts only."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "hurricane-desk" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from storm_plot import main  # noqa: E402

if __name__ == "__main__":
    raise SystemExit(main())
