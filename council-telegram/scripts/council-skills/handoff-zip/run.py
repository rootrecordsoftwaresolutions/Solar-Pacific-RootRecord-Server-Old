#!/usr/bin/env python3
"""Build an allowlisted handoff zip. Prints HANDOFF_ZIP=path for Bruce to send."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from apps.council.handoff import MARKER, bundle  # noqa: E402


def main() -> int:
    extra = [a for a in sys.argv[1:] if a and ".." not in a]
    out = bundle(extra_specs=extra)
    if not out.get("ok"):
        print("handoff failed")
        return 1
    zpath = out.get("zip") or ""
    names = ", ".join(out.get("files") or []) or "none"
    print(f"Handoff copies ({out.get('count') or 0}): {names}")
    print(f"{MARKER}{zpath}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
