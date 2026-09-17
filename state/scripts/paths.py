"""Live STATE_DIR for origin and desks."""
from __future__ import annotations

import os
from pathlib import Path

STORE = Path.home() / ".ollama" / "skills" / "state" / "store"


def state_dir() -> Path:
    raw = (os.environ.get("STATE_DIR") or os.environ.get("AVA_STATE_DIR") or "").strip()
    if raw:
        return Path(raw).expanduser().resolve()
    return STORE
