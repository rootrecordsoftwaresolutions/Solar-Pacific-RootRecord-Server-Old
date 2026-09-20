#!/usr/bin/env python3
"""Disk presence + River car want. Dry-run unless --execute."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "ecoflow-river-car" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from disk_session import prepare, release, snapshot  # noqa: E402


def main() -> int:
    execute = "--execute" in sys.argv
    if "--off" in sys.argv:
        print(json.dumps(release(execute=execute), indent=2, default=str))
        return 0
    if "--session" in sys.argv:
        from drive_automation import session

        print(json.dumps(session(execute=execute, hold="--hold" in sys.argv), indent=2, default=str))
        return 0
    if "--prepare" in sys.argv or "--on" in sys.argv:
        print(json.dumps(prepare(execute=execute), indent=2, default=str))
        return 0
    print(json.dumps(snapshot(), indent=2, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
