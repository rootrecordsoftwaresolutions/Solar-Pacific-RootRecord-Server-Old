#!/usr/bin/env bash
# Run a command with NPU memlock (64 MiB MAP_LOCKED). Same-session shells stay at 8 MiB.
set -euo pipefail
if [ "$#" -eq 0 ]; then
  set -- xrt-smi examine
fi
exec systemd-run --wait --pipe --collect -p LimitMEMLOCK=infinity -- "$@"
