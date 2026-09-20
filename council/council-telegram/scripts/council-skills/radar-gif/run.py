#!/usr/bin/env python3
"""Radar GIF posting is AWS-only — OmniBook must not attach GIFs.

Puller: rr-radar on AWS (work/radar/Current.gif).
Poster: rr-chat chat_poll on AWS (keyword → sendDocument as Bruce).
This stub exists so old catalog entries fail closed instead of serving local files.
"""
from __future__ import annotations

print(
    "Radar GIFs are posted from AWS (rr-radar + rr-chat), not from this desk. "
    "Say radar in the council group — AWS will attach Current.gif."
)
raise SystemExit(2)
