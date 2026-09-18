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
# Keep public radio URL fresh for origin/player (from latest pack or AWS file in live/)
if [[ -f "$SKILL/store/live/sysmon/"*radio-public.url ]]; then
  latest="$(ls -t "$SKILL"/store/live/sysmon/*radio-public.url 2>/dev/null | head -1)"
  if [[ -n "$latest" ]]; then
    mkdir -p "$SKILL/etc"
    cp -f "$latest" "$SKILL/store/live/sysmon/radio-public.url" 2>/dev/null || true
    cp -f "$latest" "$SKILL/etc/radio-public.url" 2>/dev/null || true
  fi
fi
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
