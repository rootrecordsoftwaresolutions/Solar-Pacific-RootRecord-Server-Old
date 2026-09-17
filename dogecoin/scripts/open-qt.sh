#!/usr/bin/env bash
# Open Dogecoin-Qt with the datadir picker. No mining. No sends from here.
set -euo pipefail
BIN="${AVA_DOGE_QT:-$HOME/.ollama/skills/dogecoin/bin/dogecoin-qt}"
export DISPLAY="${DISPLAY:-:0}"
if [ ! -x "$BIN" ]; then
  echo "dogecoin-qt missing: $BIN" >&2
  exit 1
fi
if pgrep -f "$BIN" >/dev/null 2>&1; then
  echo "dogecoin-qt already running"
  exit 0
fi
exec "$BIN" -choosedatadir "$@"
