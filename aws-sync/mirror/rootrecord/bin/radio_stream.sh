#!/usr/bin/env bash
# RootRecord Radio — always-on Icecast via radio_mix.py (duck voice over music).
set -euo pipefail
ROOT="${RR_ROOT:-/home/ubuntu/rootrecord}"
export RR_ROOT="$ROOT"
export TZ="${TZ:-Pacific/Honolulu}"
mkdir -p "$ROOT/work/audio" "$ROOT/radio/media" "$ROOT/logs" "$ROOT/etc"
PY="$ROOT/venv/bin/python"
if [[ ! -x "$PY" ]]; then PY=python3; fi
exec "$PY" "$ROOT/bin/radio_mix.py"
