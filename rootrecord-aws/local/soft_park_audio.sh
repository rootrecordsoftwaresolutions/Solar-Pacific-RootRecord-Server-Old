#!/bin/bash
# Soft-park local report *play* / radio-bus jobs once AWS radio owns audio.
set -euo pipefail
SKILLS="$HOME/.ollama/skills"
mark() {
  local dir="$1"
  [[ -d "$dir" ]] || return 0
  if [[ -f "$dir/OFFLOADED" ]]; then
    echo "already $dir"
    return 0
  fi
  cat > "$dir/OFFLOADED" <<EOF
owned_by=rr-aws-radio
reason=RootRecord radio on AWS; listen via tunnel/website
since=$(date -Iseconds)
EOF
  echo "wrote $dir/OFFLOADED"
}
mark "$SKILLS/morning-report-play"
mark "$SKILLS/midday-report-play"
mark "$SKILLS/evening-report-play"
mark "$SKILLS/late-report-play"
mark "$SKILLS/evening-report-audio"
mark "$SKILLS/hurricane-radio"
mark "$SKILLS/report-periodic-audio"
mark "$SKILLS/hourly-chime"
mark "$SKILLS/report-audio-manual"
mark "$SKILLS/morning-report-play"
# Desk speaker beds — browser stream only
mark "$SKILLS/reports-voice"
echo "Local desk radio/play soft-parked. EcoFlow untouched. Listen at https://rootrecord.cloud/radio"
