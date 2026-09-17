#!/usr/bin/env bash
# Ava launcher — the single boot-time control terminal for the whole AVA stack.
# Starts Ollama + Ava Core in the foreground. OBS stays off until Ava Ops
# flips the obs toggle and OBS is already open.
set -uo pipefail
# Background desk jobs die with this shell. nohup would leave them after the window closes.
shopt -s huponexit 2>/dev/null || true

AVA_ROOT="${AVA_CORE:-$HOME/.ollama/skills/origin}"
AVA_NS="${AVA_NS:-$HOME/.ollama/skills/origin/ns}"
AVA_DATA_DIR="${AVA_DATA_DIR:-$HOME/.ollama/skills/database/store}"
AVA_STATE_DIR="${AVA_STATE_DIR:-$HOME/.ollama/skills/state/store}"
AVA_LOGS_DIR="${AVA_LOGS_DIR:-$HOME/.ollama/skills/logs/store}"
cd "$AVA_ROOT"
VENV="$AVA_ROOT/.venv"
LOG_DIR="$AVA_LOGS_DIR"
CONSOLE_LOG="$LOG_DIR/ava-console.log"
IDLE_STOP="${IDLE_STOP:-/home/rootrecord/.ollama/skills/idle-stop/scripts/idle-stop.sh}"

mkdir -p "$LOG_DIR"

# Set the terminal window/tab title so it's identifiable at a glance.
printf '\033]0;AVA Console\007'

log() {
  printf '%s %s\n' "$(date -Iseconds)" "$*" | tee -a "$CONSOLE_LOG"
}

_AVA_STOPPING=0
ava_console_stop() {
  if [ "${_AVA_STOPPING}" = "1" ]; then
    return 0
  fi
  _AVA_STOPPING=1
  trap - EXIT INT TERM HUP
  log "AVA Console stopping — idle-stop"
  if [ -x "$IDLE_STOP" ] || [ -f "$IDLE_STOP" ]; then
    bash "$IDLE_STOP" || true
  fi
}

if [ "${AVA_CONSOLE_IDLE_STOP:-1}" != "0" ]; then
  trap 'ava_console_stop' EXIT INT TERM HUP
fi

if [ ! -x "$VENV/bin/python" ] && [ ! -x "$VENV/bin/python3" ]; then
  log "ERROR: venv python not found. Run scripts/install.sh first."
  if [ -t 0 ]; then
    read -r -p "Press Enter to close..." _
  fi
  exit 1
fi
PYTHON="$VENV/bin/python3"
if [ ! -x "$PYTHON" ]; then
  PYTHON="$VENV/bin/python"
fi

if [ ! -f "$AVA_ROOT/.env" ]; then
  log "ERROR: .env not found. Copy .env.example and fill in tokens."
  if [ -t 0 ]; then
    read -r -p "Press Enter to close..." _
  fi
  exit 1
fi

export OLLAMA_HOST="${OLLAMA_HOST:-127.0.0.1:11434}"
export OLLAMA_MODELS="${OLLAMA_MODELS:-$HOME/.ollama/models}"
# shellcheck source=ollama-env.sh
. "$HOME/.ollama/skills/ollama-env/scripts/ollama-env.sh"

log "=== AVA Console ==="
log "Root: $AVA_ROOT"
log "Log:  $CONSOLE_LOG"
export DATA_DIR="${DATA_DIR:-$AVA_DATA_DIR}"
export STATE_DIR="${STATE_DIR:-$AVA_STATE_DIR}"
export RUNTIME_LOGS="${RUNTIME_LOGS:-$AVA_LOGS_DIR}"
export PYTHONPATH="${AVA_NS}:${AVA_ROOT}${PYTHONPATH:+:$PYTHONPATH}"
echo ""

mkdir -p "$AVA_STATE_DIR"
date -Iseconds > "$AVA_STATE_DIR/ava-console-up"
echo $$ > "$AVA_STATE_DIR/ava-console.pid"

# If the window is killed hard enough that the EXIT trap misses, this session
# still runs idle-stop once the launch PID is gone.
if [ "${AVA_CONSOLE_IDLE_STOP:-1}" != "0" ]; then
  AVA_LAUNCH_PID=$$
  setsid bash -c "
    trap '' HUP INT TERM
    while kill -0 ${AVA_LAUNCH_PID} 2>/dev/null; do
      sleep 1
    done
    exec bash '${IDLE_STOP}'
  " </dev/null >/dev/null 2>&1 &
fi

# ── NPU first. GGUF on the 840M + RAM crashes this 16 GB OmniBook. ─
# AVA_FLM_AT_LAUNCH is not a second copy. Chat stays on FastFlowLM while this console is up.
if [ "${AVA_DESK_UNITS:-1}" != "0" ] && command -v systemctl >/dev/null 2>&1; then
  systemctl --user start ava-flm.service >/dev/null 2>&1 || true
  log "FastFlowLM unit started with this console"
fi
WAIT_FLM="$HOME/.ollama/skills/host-metrics/scripts/wait-flm.sh"
if [ "${AVA_NPU_CHAT:-1}" != "0" ] && [ -x "$WAIT_FLM" ]; then
  log "Waiting for FastFlowLM (NPU) before origin..."
  if "$WAIT_FLM"; then
    log "FastFlowLM ready on the NPU — chat will not map GGUF"
  else
    log "FastFlowLM not ready — not mapping GGUF chat (NPU only)"
  fi
fi

# Ollama stays for coder/vision only. Do not warm llama GGUF alongside the NPU.
if ! pgrep -f "ollama serve" >/dev/null 2>&1; then
  log "Starting Ollama local server (coder/vision only)..."
  ollama serve >> "$LOG_DIR/ollama.log" 2>&1 &
  for _ in $(seq 1 30); do
    if curl -fsS "http://$OLLAMA_HOST/api/tags" >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
else
  log "Ollama already running."
fi
if [ "${AVA_NPU_CHAT:-1}" = "0" ] && [ "${AVA_OLLAMA_WARM:-1}" != "0" ]; then
  log "Warming ${AVA_OLLAMA_MODEL:-llama3.2:3b-instruct-q4_K_M} (${AVA_OLLAMA_CHAT_KEEP_ALIVE:-15m})..."
  ollama_warm_chat
fi

# ── Other desk processors owned by this console (stopped on close) ───────────
if [ "${AVA_DESK_UNITS:-1}" != "0" ] && command -v systemctl >/dev/null 2>&1; then
  for _unit in ava-ecoflow-ble.service ava-hybrid-night.service ava-auto-push.timer; do
    systemctl --user start "$_unit" >/dev/null 2>&1 || true
  done
  log "Desk units started with this console (BLE, hybrid, auto-push timer)"
else
  log "Desk units skipped (AVA_DESK_UNITS=0)"
fi
if command -v bluetoothctl >/dev/null 2>&1; then
  bluetoothctl pairable on >/dev/null 2>&1 || true
  bluetoothctl discoverable on >/dev/null 2>&1 || true
fi

BT_BRIDGE="/home/rootrecord/.ollama/skills/ava-ops/scripts/ava_bt_bridge.py"
if systemctl --user is-enabled ava-bt-bridge.service >/dev/null 2>&1; then
  log "Ava Ops Bluetooth bridge owned by systemd — not starting from launch.sh"
elif [ -f "$BT_BRIDGE" ]; then
  if ! pgrep -f "ava_bt_bridge.py" >/dev/null 2>&1; then
    log "Starting Ava Ops Bluetooth bridge..."
    python3 "$BT_BRIDGE" >> "$LOG_DIR/ava-bt-bridge.log" 2>&1 &
  else
    log "Ava Ops Bluetooth bridge already running."
  fi
fi

# ── Kill any leftover processes on port 8787 ──────────────────────────────────
if lsof -ti:8787 &>/dev/null; then
  log "Stopping existing process on :8787..."
  kill $(lsof -ti:8787) 2>/dev/null || true
  sleep 1
fi

# Telegram council (Ava/Bruce/Carly) — desk discussion. Not OBS. Idle-stop kills it.
# If the user unit is enabled, systemd owns the process.
COUNCIL_LOG="$LOG_DIR/ava-council.log"
if systemctl --user is-enabled ava-council.service >/dev/null 2>&1; then
  log "Telegram council owned by systemd — starting unit if needed"
  systemctl --user start ava-council.service >/dev/null 2>&1 || true
elif ! pgrep -f "python.*-m apps.council" >/dev/null 2>&1; then
  (
    for _ in $(seq 1 40); do
      if curl -fsS "http://127.0.0.1:8787/health" >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
    log "Starting Telegram council (Ava/Bruce/Carly)..."
    cd "$AVA_ROOT"
    exec "$PYTHON" -m apps.council >> "$COUNCIL_LOG" 2>&1
  ) &
else
  log "Telegram council already running."
fi

API_TUNNEL="$HOME/.ollama/skills/public-edge/scripts/api-tunnel.sh"
if pgrep -x cloudflared >/dev/null 2>&1; then
  log "API tunnel already running"
elif [ -f "$API_TUNNEL" ]; then
  (
    for _ in $(seq 1 40); do
      if curl -fsS "http://127.0.0.1:8787/health" >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
    log "Starting API tunnel..."
    exec bash "$API_TUNNEL"
  ) >> "$LOG_DIR/cloudflared.log" 2>&1 &
fi

# Ava Core owns voice lifecycle in-process. OBS jobs stay off until Ava Ops
# flips the obs toggle and OBS is already open. One process, one log stream.
# Non-zero uvicorn exit (recycle-origin SIGTERM) restarts origin without idle-stop.
# Clean exit 0 (tests / orderly stop) does not loop.
# Crash-loop cap: AVA_ORIGIN_RECYCLE_MAX (default 20) with linear backoff to 30s.
log "Starting Ava Core on :8787..."
echo ""
ORIGIN_CODE=0
ORIGIN_FAILS=0
ORIGIN_RECYCLE_MAX="${AVA_ORIGIN_RECYCLE_MAX:-20}"
UVICORN_BIN="$VENV/bin/uvicorn"
if [ ! -x "$UVICORN_BIN" ]; then
  UVICORN_BIN="$PYTHON -m uvicorn"
fi
while true; do
  set +o pipefail
  if [ -x "$VENV/bin/uvicorn" ]; then
    "$VENV/bin/uvicorn" apps.core.main:app \
      --host 0.0.0.0 \
      --port 8787 \
      --log-level info \
      --no-access-log \
      2>&1 | tee -a "$CONSOLE_LOG" "$LOG_DIR/ava-core.log"
  else
    "$PYTHON" -m uvicorn apps.core.main:app \
      --host 0.0.0.0 \
      --port 8787 \
      --log-level info \
      --no-access-log \
      2>&1 | tee -a "$CONSOLE_LOG" "$LOG_DIR/ava-core.log"
  fi
  ORIGIN_CODE=${PIPESTATUS[0]:-$?}
  set -o pipefail
  if [ "${_AVA_STOPPING}" = "1" ]; then
    break
  fi
  if [ "${ORIGIN_CODE}" = "0" ]; then
    break
  fi
  if [ "${AVA_ORIGIN_RECYCLE:-1}" != "1" ]; then
    break
  fi
  ORIGIN_FAILS=$((ORIGIN_FAILS + 1))
  if [ "${ORIGIN_FAILS}" -ge "${ORIGIN_RECYCLE_MAX}" ]; then
    log "Ava Core exited (code ${ORIGIN_CODE}) — recycle cap ${ORIGIN_RECYCLE_MAX} reached; stopping"
    break
  fi
  DELAY=$((ORIGIN_FAILS * 2))
  if [ "${DELAY}" -gt 30 ]; then
    DELAY=30
  fi
  log "Ava Core exited (code ${ORIGIN_CODE}) — recycling origin in ${DELAY}s (attempt ${ORIGIN_FAILS}/${ORIGIN_RECYCLE_MAX})"
  sleep "${DELAY}"
done

log "Ava Core exited (code ${ORIGIN_CODE})."
if [ -t 0 ] && [ "${_AVA_STOPPING}" != "1" ]; then
  read -r -p "Press Enter to close this console..." _
fi
