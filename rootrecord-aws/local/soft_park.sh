#!/usr/bin/env bash
# Soft-park local weather/quake/radar/hurricane/noaa jobs (do not delete skill trees).
set -euo pipefail
SKILLS="$HOME/.ollama/skills"
mark() {
  local dir="$1"
  mkdir -p "$dir"
  if [[ -f "$dir/OFFLOADED" ]]; then
    echo "already: $dir/OFFLOADED"
  else
    cat > "$dir/OFFLOADED" <<EOF
owned_by=rr-aws
reason=RootRecord AWS datapack collector
since=$(date -Iseconds)
EOF
    echo "wrote $dir/OFFLOADED"
  fi
}
mark "$SKILLS/nws-hawaii"
mark "$SKILLS/council-quake"
mark "$SKILLS/earthquake-hourly"
mark "$SKILLS/radar-archive"
mark "$SKILLS/rr-kilauea"
mark "$SKILLS/hurricane-fetch"
mark "$SKILLS/hurricane-tracker"
mark "$SKILLS/rr-noaa"
echo "EcoFlow left untouched."
echo "NOTE: scheduler skips OFFLOADED skill roots (scheduler-clock)."
