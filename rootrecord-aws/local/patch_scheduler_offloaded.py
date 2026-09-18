#!/usr/bin/env python3
"""Deprecated — OFFLOADED handling lives in scheduler-clock.

scheduler-clock/scripts/scheduler.py:
  - _skill_offloaded() + _WaveScheduler.add_job → never register OFFLOADED skill crons
  - _run() keeps a fire-time safety skip

soft_park.sh / soft_park_audio.sh only write OFFLOADED markers (do not delete trees).
"""
print(
    "OFFLOADED: not registered at boot "
    "(scheduler-clock _WaveScheduler + _skill_offloaded)"
)
