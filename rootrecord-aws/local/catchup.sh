#!/usr/bin/env bash
# One-shot catch-up after desk downtime: pull every missed Telegram pack, prep, optional publish.
set -euo pipefail
SKILL="$HOME/.ollama/skills/rootrecord-aws"
PY="${RR_PYTHON:-$SKILL/.venv/bin/python}"
[[ -x "$PY" ]] || PY=python3
export TZ=Pacific/Honolulu
cd "$SKILL"
echo "=== catch-up ingest ==="
"$PY" "$SKILL/local/ingest.py"
echo "=== report prep ==="
"$PY" "$SKILL/local/report_prep.py"
# Publish only if we are on a publish mark
min="$(date +%M)"
case "$min" in
  00|15|30|45)
    echo "=== publish (on mark) ==="
    "$PY" "$SKILL/local/publish.py"
    ;;
  *)
    echo "=== publish skipped (minute=$min; next :00/:15/:30/:45) ==="
    ;;
esac
echo CATCHUP_OK
