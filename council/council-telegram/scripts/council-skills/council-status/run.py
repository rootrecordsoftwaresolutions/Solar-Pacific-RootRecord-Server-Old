#!/usr/bin/env python3
"""Allowlisted council status — no tokens."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from apps.council import ollama_ctl, state  # noqa: E402
from apps.council.config import load_config  # noqa: E402


def main() -> int:
    cfg = load_config()
    st = state.load_state(cfg.state_path)
    ollama_up = ollama_ctl.is_up(cfg)
    flm_up = ollama_ctl.flm_is_up()
    voices = ollama_ctl.voices_up(cfg)
    print(state.status_lines(st, cfg, ollama_up, flm_up=flm_up, voices_up=voices))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
