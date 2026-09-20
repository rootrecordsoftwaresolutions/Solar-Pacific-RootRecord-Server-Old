#!/usr/bin/env python3
"""Show Rear Shed panels still. Powers River car DC if needed."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path.home() / ".ollama" / "skills" / "panels-cam" / "scripts"
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from panels_grab import main  # noqa: E402

if __name__ == "__main__":
    # Council always wants a real grab when invoked
    argv = list(sys.argv[1:])
    if not any(a in {"--show", "--cycle", "--tick", "--status", "--auto"} for a in argv):
        argv = ["--show", *argv]
    if "--dry-run" not in argv and "--execute" not in argv and "--status" not in argv:
        # show path implies execute
        pass
    raise SystemExit(main(argv))
