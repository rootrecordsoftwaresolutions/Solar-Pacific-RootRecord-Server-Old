#!/usr/bin/env python3
"""Download hexgrad/Kokoro-82M into this skill store."""
from __future__ import annotations

from pathlib import Path

REPO_ID = "hexgrad/Kokoro-82M"
STORE = Path.home() / ".ollama" / "skills" / "kokoro" / "store" / "Kokoro-82M"


def main() -> None:
    from huggingface_hub import snapshot_download

    STORE.parent.mkdir(parents=True, exist_ok=True)
    path = snapshot_download(
        repo_id=REPO_ID,
        local_dir=str(STORE),
    )
    print(path)


if __name__ == "__main__":
    main()
