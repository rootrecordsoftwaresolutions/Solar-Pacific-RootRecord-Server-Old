#!/usr/bin/env bash
# Open Bitcoin-Qt with the datadir picker. No mining. No sends from here.
set -euo pipefail
BIN="${AVA_BTC_QT:-$HOME/.ollama/skills/bitcoin/bin/bitcoin-qt}"
export DISPLAY="${DISPLAY:-:0}"
if [ ! -x "$BIN" ]; then
  echo "bitcoin-qt missing: $BIN" >&2
  exit 1
fi
if pgrep -f "$BIN" >/dev/null 2>&1; then
  echo "bitcoin-qt already running"
  exit 0
fi
exec "$BIN" -choosedatadir "$@"
