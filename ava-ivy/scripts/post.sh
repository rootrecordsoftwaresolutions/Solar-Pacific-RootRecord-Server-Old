#!/usr/bin/env bash
set -euo pipefail
ROOT="${AVA_CORE:-$HOME/RootRecord/Ava-Core}"
PY="$ROOT/.venv/bin/python"
if [[ ! -x "$PY" ]]; then PY=python3; fi
export PYTHONPATH="$HOME/.ollama/skills/origin/ns:$ROOT${PYTHONPATH:+:$PYTHONPATH}"
exec "$PY" -m apps.council post --voice ava "$@"
