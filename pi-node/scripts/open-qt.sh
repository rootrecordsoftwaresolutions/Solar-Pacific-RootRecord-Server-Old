#!/usr/bin/env bash
# Pick Pi data folder, then run official initialize in a terminal.
# Do not pass seeds or postgres passwords here.
set -euo pipefail
ROOT="${AVA_PI_NODE:-$HOME/.ollama/skills/pi-node}"
BIN="${AVA_PI_NODE_BIN:-}"
if [ -z "$BIN" ]; then
  if command -v pi-node >/dev/null 2>&1; then
    BIN=$(command -v pi-node)
  else
    BIN="$ROOT/bin/pi-node"
  fi
fi
STATE="$ROOT/state"
mkdir -p "$STATE"
export DISPLAY="${DISPLAY:-:0}"
if [ ! -x "$BIN" ]; then
  echo "pi-node missing: $BIN" >&2
  exit 1
fi
if ! command -v zenity >/dev/null 2>&1; then
  echo "zenity missing" >&2
  exit 1
fi
dir=$(zenity --file-selection --directory --title="Pi Node — choose data directory" --filename="/mnt/Archives/" || true)
if [ -z "${dir:-}" ]; then
  echo "cancelled"
  exit 0
fi
printf '%s\n' "$dir" > "$STATE/pi-folder.txt"
run="cd $(printf %q "$dir") && $(printf %q "$BIN") initialize --pi-folder $(printf %q "$dir") --docker-volumes $(printf %q "$dir/docker_volumes"); echo; echo '[ava] initialize exited' \$?; read -r _"
term=$(command -v gnome-terminal || command -v xfce4-terminal || command -v x-terminal-emulator || true)
if [ -n "$term" ] && [[ "$term" == *gnome-terminal ]]; then
  exec "$term" --title="Pi Node initialize" -- bash -lc "$run"
fi
if [ -n "$term" ] && [[ "$term" == *xfce4-terminal ]]; then
  exec "$term" --title="Pi Node initialize" -e "bash -lc $(printf %q "$run")"
fi
exec bash -lc "$run"
