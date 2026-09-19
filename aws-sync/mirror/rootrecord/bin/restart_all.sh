#!/usr/bin/env bash
# Restart RootRecord units so drop-in / config edits take effect without manual commands.
# Skips cloudflared by default — quick-tunnel URLs change on restart; use named token for stable radio.
set -euo pipefail
UNITS=(
  rr-weather rr-earthquake rr-radar rr-solar-cam rr-kilauea rr-hurricane rr-noaa
  rr-chat rr-audio-recv rr-dropins
  rr-icecast rr-radio rr-youtube
)
if [[ "${RR_RESTART_CLOUDFLARED:-0}" == "1" ]]; then
  UNITS+=(rr-cloudflared)
fi
# packer restarts last so we don't kill ourselves mid-script when called from packer —
# packer invokes this via systemd-run or after send; exclude self if RR_SKIP_PACKER=1
if [[ "${RR_SKIP_PACKER:-0}" != "1" ]]; then
  UNITS+=(rr-packer)
fi
for u in "${UNITS[@]}"; do
  systemctl restart "$u.service" 2>/dev/null || systemctl restart "$u" 2>/dev/null || true
done
echo "restarted ${#UNITS[@]} units"
