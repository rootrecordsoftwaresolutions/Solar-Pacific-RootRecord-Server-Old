#!/usr/bin/env bash
set -euo pipefail
SKILL="${HOME}/.ollama/skills/kokoro"
mkdir -p "${SKILL}/store"
uv venv --python 3.12 "${SKILL}/store/venv"
PY="${SKILL}/store/venv/bin/python"
uv pip install --python "$PY" torch --index-url https://download.pytorch.org/whl/cpu
uv pip install --python "$PY" -r "${SKILL}/requirements.txt"
uv pip install --python "$PY" \
  https://github.com/explosion/spacy-models/releases/download/en_core_web_sm-3.8.0/en_core_web_sm-3.8.0-py3-none-any.whl
"$PY" "${SKILL}/scripts/pull_model.py"
