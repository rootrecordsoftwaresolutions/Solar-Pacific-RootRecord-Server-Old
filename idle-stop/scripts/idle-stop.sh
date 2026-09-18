#!/usr/bin/env bash
# AVA Console / Idle desk: stop everything this desk started. Do not power off the PC.
set -u

DRY_RUN="${IDLE_STOP_DRY_RUN:-0}"

AVA_ROOT="${AVA_CORE:-/home/rootrecord/.ollama/skills/origin}"
AVA_NS="${AVA_NS:-$HOME/.ollama/skills/origin/ns}"
LOG_DIR="${AVA_LOGS_DIR:-$HOME/.ollama/skills/logs/store}"
STATE_DIR="${AVA_STATE_DIR:-$HOME/.ollama/skills/state/store}"
mkdir -p "$LOG_DIR" "$STATE_DIR"

log() {
  printf '%s %s\n' "$(date -Iseconds)" "$*" | tee -a "$LOG_DIR/idle-stop.log"
}

# Refuse if a *different* live AVA Console owns the desk. Stops a dying console's
# trap/reaper from tearing down a newer launch that already claimed the pid file.
LIVE_OWNER="$(cat "$STATE_DIR/ava-console.pid" 2>/dev/null || true)"
REQ_OWNER="${IDLE_STOP_OWNER_PID:-}"
if [ -n "$LIVE_OWNER" ] && kill -0 "$LIVE_OWNER" 2>/dev/null; then
  if [ -z "$REQ_OWNER" ] || [ "$REQ_OWNER" != "$LIVE_OWNER" ]; then
    log "idle-stop: refused — live AVA Console pid $LIVE_OWNER still owns the desk"
    exit 0
  fi
fi

if [ "$DRY_RUN" != "1" ]; then
  python3 "$HOME/.ollama/skills/xmrig/scripts/xmrig_ctl.py" stop >/dev/null 2>&1 || true
fi

UNITS=(
  ava-council.service
  ava-bt-bridge.service
  ava-flm.service
  ava-ecoflow-ble.service
  ava-hybrid-night.service
  ava-auto-push.timer
  ava-auto-push.service
)

# Tell the group we are going dark before we kill the listener.
if [ "$DRY_RUN" != "1" ]; then
  PY="$AVA_ROOT/.venv/bin/python"
  if [ ! -x "$PY" ]; then
    PY="$AVA_ROOT/.venv/bin/python3"
  fi
  if [ -x "$PY" ]; then
    PYTHONPATH="${AVA_NS}:${AVA_ROOT}" "$PY" -c "from apps.council.power_status import announce; announce('down', force=True)" \
      >/dev/null 2>&1 || true
  fi
fi

if command -v systemctl >/dev/null 2>&1; then
  if [ "$DRY_RUN" = "1" ]; then
    log "idle-stop: would stop systemd --user ${UNITS[*]}"
  else
    # Drop the console-up flag first so unit ConditionPathExists fails on restart.
    rm -f "$STATE_DIR/ava-console-up" "$STATE_DIR/ava-console.pid"
    systemctl --user stop "${UNITS[@]}" >/dev/null 2>&1 || true
    systemctl --user stop ollama.service >/dev/null 2>&1 || true
    systemctl stop ollama.service >/dev/null 2>&1 || true
  fi
fi

# Every processor the console owns. No leftovers while the terminal is closed.
patterns=(
  'uvicorn.*apps\.core\.main:app'
  'python.*apps\.core\.main'
  'python.*-m apps.council'
  'python.*apps\.council'
  'python.*apps\.voice\.director'
  'ava_bt_bridge\.py'
  'ecoflow_ble_poller\.py'
  'hybrid_night_poller\.py'
  'auto-push\.sh'
  'ollama serve'
  'flm-real serve'
  'fastflowlm/flm serve'
  'flm serve'
  'ffplay.*AVA_MUSIC_BED'
  'mpg123.*AVA_MUSIC_BED'
  'play_music_bed'
  'obs-studio'
  'poller.*\.mjs'
  'local-edge.*server\.mjs'
  'node.*server\.mjs'
  'cloudflared'
  '/.ollama/skills/xmrig/runtime/xmrig'
)

pids=()
for pattern in "${patterns[@]}"; do
  while read -r pid; do
    [ -n "$pid" ] || continue
    pids+=("$pid")
  done < <(ps -eo pid=,args= 2>/dev/null | grep -E "$pattern" | grep -v grep | awk '{print $1}' | sort -u)
done

for port in 8787 8791 11434 52625; do
  if command -v lsof >/dev/null 2>&1; then
    while read -r pid; do
      [ -n "$pid" ] || continue
      pids+=("$pid")
    done < <(lsof -ti "tcp:$port" 2>/dev/null || true)
  fi
done

if [ "${#pids[@]}" -eq 0 ]; then
  rm -f "$STATE_DIR/ava-console-up" "$STATE_DIR/ava-console.pid"
  log "idle-stop: no monitored desk processes found; system is already idle"
  exit 0
fi

for pid in $(printf '%s\n' "${pids[@]}" | sort -u); do
  if [ "$DRY_RUN" = "1" ]; then
    log "idle-stop: would stop PID $pid"
    continue
  fi
  if kill -TERM "$pid" 2>/dev/null; then
    log "idle-stop: sent TERM to PID $pid"
  fi
done

if [ "$DRY_RUN" != "1" ]; then
  sleep 3
fi
for pid in $(printf '%s\n' "${pids[@]}" | sort -u); do
  [ "$DRY_RUN" = "1" ] && continue
  if kill -0 "$pid" 2>/dev/null; then
    kill -KILL "$pid" 2>/dev/null || true
    log "idle-stop: force-killed PID $pid"
  fi
done

for pattern in "${patterns[@]}"; do
  [ "$DRY_RUN" = "1" ] && continue
  pkill -f "$pattern" 2>/dev/null || true
done

if [ "$DRY_RUN" != "1" ]; then
  rm -f "$STATE_DIR/ava-console-up" "$STATE_DIR/ava-console.pid"
fi

log "idle-stop: desk returned to idle state"
