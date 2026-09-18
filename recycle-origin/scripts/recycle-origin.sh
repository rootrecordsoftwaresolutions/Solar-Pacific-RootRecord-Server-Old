#!/usr/bin/env bash
# Recycle Ava Core on :8787 only. Does not idle-stop. Does not reboot the PC.
# AVA Console must loop uvicorn on non-zero exit (scripts/launch.sh).
#
# Only kill processes *listening* on :8787. Clients (cloudflared, curl, council
# probes) also show up in `lsof -ti:8787` and must not be torn down with origin.
set -uo pipefail
ROOT="/home/rootrecord/.ollama/skills/origin"
LOG_DIR="${AVA_LOGS_DIR:-$HOME/.ollama/skills/logs/store}"
mkdir -p "$LOG_DIR"
log() { printf '%s %s\n' "$(date -Iseconds)" "$*" | tee -a "$LOG_DIR/recycle-origin.log"; }

listen_pids() {
  if command -v lsof >/dev/null 2>&1; then
    # -sTCP:LISTEN keeps tunnel/clients off the kill list
    lsof -nP -iTCP:8787 -sTCP:LISTEN -t 2>/dev/null | sort -u || true
    return
  fi
  if command -v ss >/dev/null 2>&1; then
    ss -lntp "sport = :8787" 2>/dev/null \
      | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' \
      | sort -u || true
  fi
}

PIDS="$(listen_pids)"
if [ -z "$PIDS" ]; then
  log "recycle-origin: nothing listening on :8787"
  exit 0
fi
log "recycle-origin: signaling listeners $PIDS"
# shellcheck disable=SC2086
kill $PIDS 2>/dev/null || true
sleep 1
STILL="$(listen_pids)"
if [ -n "$STILL" ]; then
  log "recycle-origin: force $STILL"
  # shellcheck disable=SC2086
  kill -9 $STILL 2>/dev/null || true
fi
log "recycle-origin: done"
exit 0
