"""Live DATA_DIR for origin and desks."""
from __future__ import annotations

import os
from pathlib import Path

STORE = Path.home() / ".ollama" / "skills" / "database" / "store"
STATE = Path.home() / ".ollama" / "skills" / "state" / "store"
LOGS = Path.home() / ".ollama" / "skills" / "logs" / "store"


def data_dir() -> Path:
    raw = (os.environ.get("DATA_DIR") or os.environ.get("AVA_DATA_DIR") or "").strip()
    if raw:
        return Path(raw).expanduser().resolve()
    return STORE
