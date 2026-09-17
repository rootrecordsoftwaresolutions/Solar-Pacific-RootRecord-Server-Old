#!/usr/bin/env bash
# Recycle Ava Core on :8787 only. Does not idle-stop. Does not reboot the PC.
# AVA Console must loop uvicorn on non-zero exit (scripts/launch.sh).
set -uo pipefail
ROOT="/home/rootrecord/.ollama/skills/origin"
LOG_DIR="${AVA_LOGS_DIR:-$HOME/.ollama/skills/logs/store}"
mkdir -p "$LOG_DIR"
log() { printf '%s %s\n' "$(date -Iseconds)" "$*" | tee -a "$LOG_DIR/recycle-origin.log"; }

PIDS=""
if command -v lsof >/dev/null 2>&1; then
  PIDS="$(lsof -ti:8787 2>/dev/null || true)"
fi
if [ -z "$PIDS" ]; then
  log "recycle-origin: nothing on :8787"
  exit 0
fi
log "recycle-origin: signaling $PIDS"
# shellcheck disable=SC2086
kill $PIDS 2>/dev/null || true
sleep 1
STILL=""
if command -v lsof >/dev/null 2>&1; then
  STILL="$(lsof -ti:8787 2>/dev/null || true)"
fi
if [ -n "$STILL" ]; then
  log "recycle-origin: force $STILL"
  # shellcheck disable=SC2086
  kill -9 $STILL 2>/dev/null || true
fi
log "recycle-origin: done"
exit 0
