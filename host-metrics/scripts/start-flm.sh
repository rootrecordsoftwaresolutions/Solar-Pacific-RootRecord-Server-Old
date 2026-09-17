#!/usr/bin/env bash
# FastFlowLM on the XDNA NPU. Not Ollama. Chat model lives here.
set -euo pipefail
FLM="${FLM_BIN:-$HOME/.local/opt/fastflowlm/flm}"
LOG_DIR="${AVA_LOGS_DIR:-$HOME/.ollama/skills/logs/store}"
LOG="$LOG_DIR/flm.log"
HOST="${AVA_FLM_HOST:-127.0.0.1}"
PORT="${AVA_FLM_PORT:-52625}"
MODEL="${AVA_FLM_MODEL:-llama3.2:3b}"
mkdir -p "$LOG_DIR"

if [ ! -x "$FLM" ]; then
  echo "flm missing: $FLM" >&2
  exit 1
fi

if ! bash "$HOME/.ollama/skills/idle-stop/scripts/console-up.sh"; then
  echo "AVA Console is closed; not starting FastFlowLM" >&2
  exit 1
fi

if curl -fsS --max-time 2 "http://${HOST}:${PORT}/v1/models" >/dev/null 2>&1; then
  echo "flm already serving on ${HOST}:${PORT}"
  exit 0
fi

# default, not performance — performance at login crashed GNOME on this OmniBook
PMODE="${AVA_FLM_PMODE:-default}"
"$FLM" serve "$MODEL" --pmode "$PMODE" --host "$HOST" --port "$PORT" >>"$LOG" 2>&1 &
for _ in $(seq 1 60); do
  if curl -fsS --max-time 2 "http://${HOST}:${PORT}/v1/models" >/dev/null 2>&1; then
    echo "flm ready on ${HOST}:${PORT} model=${MODEL}"
    exit 0
  fi
  sleep 1
done
echo "flm did not become ready; see $LOG" >&2
exit 1
