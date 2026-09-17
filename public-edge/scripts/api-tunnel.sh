#!/usr/bin/env bash
# API tunnel for Vercel / Workers. Console-owned. Token file is never printed.
set -euo pipefail
STATE_DIR="${AVA_STATE_DIR:-$HOME/.ollama/skills/state/store}"
LOG_DIR="${AVA_LOGS_DIR:-$HOME/.ollama/skills/logs/store}"
TOKEN_FILE="${CLOUDFLARED_TOKEN_FILE:-$HOME/.cloudflared/origin.token}"
LOG="$LOG_DIR/cloudflared.log"
PATH="$HOME/.local/bin:$PATH"
BIN="$(command -v cloudflared || true)"
if [ -z "$BIN" ] && [ -x "$HOME/.local/bin/cloudflared" ]; then
  BIN="$HOME/.local/bin/cloudflared"
fi
mkdir -p "$LOG_DIR" "$STATE_DIR"

if [ ! -f "$STATE_DIR/ava-console-up" ]; then
  echo "api-tunnel: console is closed — not starting" >&2
  exit 0
fi
if [ -z "$BIN" ]; then
  echo "api-tunnel: cloudflared not installed" >&2
  exit 1
fi
if [ ! -s "$TOKEN_FILE" ]; then
  echo "api-tunnel: missing token file" >&2
  exit 1
fi
if pgrep -x cloudflared >/dev/null 2>&1; then
  echo "api-tunnel: already running"
  exit 0
fi
TOKEN="$(tr -d '\n\r' < "$TOKEN_FILE")"
exec "$BIN" tunnel --no-autoupdate run --token "$TOKEN" >>"$LOG" 2>&1
