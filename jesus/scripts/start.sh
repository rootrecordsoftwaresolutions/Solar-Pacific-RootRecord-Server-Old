#!/usr/bin/env bash
set -euo pipefail
ROOT="$HOME/.ollama/skills/jesus"
cd "$ROOT"
exec python3 "$ROOT/bot.py"
