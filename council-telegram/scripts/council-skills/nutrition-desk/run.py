#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "nutrition" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from foods import main as foods_main  # noqa: E402

if __name__ == "__main__":
    raise SystemExit(foods_main())
