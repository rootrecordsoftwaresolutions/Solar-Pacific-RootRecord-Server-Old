#!/usr/bin/env bash
# Health check only. Never start origin or Ollama while AVA Console is closed.
set -euo pipefail

HEALTH_URL="${AVA_HEALTH_URL:-http://127.0.0.1:8787/health}"
CHECK_OLLAMA="${AVA_CHECK_OLLAMA:-1}"
OLLAMA_URL="${AVA_OLLAMA_URL:-http://127.0.0.1:11434/api/tags}"
CONSOLE_UP="$HOME/.ollama/skills/idle-stop/scripts/console-up.sh"
# shellcheck source=ollama-env.sh
. /home/rootrecord/.ollama/skills/ollama-env/scripts/ollama-env.sh

log() {
  printf '%s %s\n' "$(date -Iseconds)" "$*"
}

if ! bash "$CONSOLE_UP"; then
  log "AVA Console is closed; not starting origin or Ollama"
  exit 0
fi

health_ok=0
if curl -fsS --max-time 4 "$HEALTH_URL" >/dev/null 2>&1; then
  health_ok=1
fi

if [ "$health_ok" -ne 1 ]; then
  log "origin health check failed; launch.sh owns recycle — not starting a detached copy"
  exit 1
fi

if [ "$CHECK_OLLAMA" = "1" ]; then
  if ! curl -fsS --max-time 4 "$OLLAMA_URL" >/dev/null 2>&1; then
    log "ollama not responding; launch.sh owns it — not starting a detached copy"
    exit 1
  fi
fi

if [ "${AVA_CHECK_FLM:-0}" = "1" ]; then
  if ! curl -fsS --max-time 4 "http://127.0.0.1:${AVA_FLM_PORT:-52625}/v1/models" >/dev/null 2>&1; then
    log "flm not responding; launch.sh owns ava-flm.service — not starting a detached copy"
    exit 1
  fi
fi

log "runtime check ok"
