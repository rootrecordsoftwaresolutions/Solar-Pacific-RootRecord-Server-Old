#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "cooking" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from recipes import main as recipes_main  # noqa: E402

if __name__ == "__main__":
    raise SystemExit(recipes_main())
