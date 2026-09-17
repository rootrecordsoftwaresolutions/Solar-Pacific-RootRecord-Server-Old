#!/usr/bin/env bash
# Login autostart: skip AVA Console while Starlink night-sleep is active.
sleep 5
AVA_CORE="${AVA_CORE:-$HOME/.ollama/skills/origin}"
LAUNCH="/home/rootrecord/.ollama/skills/launch/scripts/launch.sh"
VENV="$AVA_CORE/.venv/bin/python"
OPS="$HOME/.ollama/skills/ecoflow-ble-poller/store"
if [ -x "$VENV" ] && [ -f "$OPS/ecoflow_ble_poller.py" ]; then
  if "$VENV" -c "import sys; sys.path.insert(0, r'$OPS'); import ecoflow_ble_poller as p; raise SystemExit(0 if p.in_starlink_sleep() else 1)"; then
    echo "$(date -Iseconds) night sleep: skip AVA Console autostart"
    exit 0
  fi
fi
exec "$LAUNCH"
