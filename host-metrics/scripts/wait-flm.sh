#!/usr/bin/env bash
# Block until FastFlowLM answers on :52625. Does not spawn a second flm.
set -uo pipefail
if [ "${AVA_NPU_CHAT:-1}" = "0" ]; then
  exit 0
fi
HOST="${AVA_FLM_HOST:-127.0.0.1}"
PORT="${AVA_FLM_PORT:-52625}"
TRIES="${AVA_FLM_WAIT_S:-180}"
i=0
while [ "$i" -lt "$TRIES" ]; do
  if curl -fsS --max-time 2 "http://${HOST}:${PORT}/v1/models" >/dev/null 2>&1; then
    exit 0
  fi
  i=$((i + 1))
  sleep 1
done
echo "wait-flm: ${HOST}:${PORT} not ready after ${TRIES}s" >&2
exit 1
