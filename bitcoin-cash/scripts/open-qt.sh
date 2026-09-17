#!/usr/bin/env bash
# Open Bitcoin Cash Node Qt with the datadir picker. No mining. No sends from here.
set -euo pipefail
BIN="${AVA_BCH_QT:-$HOME/.ollama/skills/bitcoin-cash/bin/bitcoin-qt}"
export DISPLAY="${DISPLAY:-:0}"
if [ ! -x "$BIN" ]; then
  echo "bitcoin-qt missing: $BIN" >&2
  exit 1
fi
if pgrep -f "$BIN" >/dev/null 2>&1; then
  echo "bitcoin-cash qt already running"
  exit 0
fi
exec "$BIN" -choosedatadir "$@"
