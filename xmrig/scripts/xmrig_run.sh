#!/bin/bash
# Root-only. Exec XMRig with the skill config (MSR needs this).
set -euo pipefail
SKILL="/home/rootrecord/.ollama/skills/xmrig"
BIN="$SKILL/runtime/xmrig"
CFG="$SKILL/runtime/config.json"
if [ "$(id -u)" -ne 0 ]; then
  echo "need_root" >&2
  exit 1
fi
if [ ! -x "$BIN" ] || [ ! -f "$CFG" ]; then
  echo "runtime_missing" >&2
  exit 1
fi
cd "$SKILL/runtime"
exec "$BIN" --config "$CFG"
