#!/usr/bin/env python3
"""Allowlisted repo read — no secrets."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from apps.council.desk_read import read_spec, snapshot_for_prompt  # noqa: E402


def main() -> int:
    spec = sys.argv[1] if len(sys.argv) > 1 else ""
    if not spec:
        print(snapshot_for_prompt(cap=3500))
        return 0
    print(read_spec(spec))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
