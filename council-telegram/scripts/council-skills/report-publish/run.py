#!/usr/bin/env python3
"""Publish queued public reports through local origin. No operator click."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from apps.council.report_gate import publish_pending  # noqa: E402


def main() -> int:
    out = publish_pending()
    n = int(out.get("published") or 0)
    if n:
        print(f"Published {n} report draft(s). Council handles report publish.")
    else:
        print("No report drafts in the queue.")
    if out.get("failed"):
        print("failed: " + ", ".join(out["failed"]))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
