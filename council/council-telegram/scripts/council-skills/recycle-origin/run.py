#!/usr/bin/env python3
"""Recycle Ava Core on :8787. No PC reboot. No idle-stop."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from apps.council.self_repair import recycle_origin  # noqa: E402


def main() -> int:
    out = recycle_origin(force=True)
    print(out.get("detail") or ("ok" if out.get("ok") else "fail"))
    return 0 if out.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
