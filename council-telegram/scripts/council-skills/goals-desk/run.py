#!/usr/bin/env python3
"""Council goals list/add. Local store. No invented USD."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "goals" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from council_goals import main as goals_main  # noqa: E402

if __name__ == "__main__":
    raise SystemExit(goals_main())
