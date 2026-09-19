#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG="$DIR/logs/bisync-$(date +%Y%m%d-%H%M%S).log"
rclone --config "$DIR/config/rclone.conf" bisync \
  ec2:/home/ubuntu \
  "$DIR/mirror" \
  --create-empty-src-dirs \
  --compare size,modtime,checksum \
  --slow-hash-sync-only \
  --resilient \
  --recover \
  --max-lock 2m \
  --conflict-resolve newer \
  --conflict-loser delete \
  --log-file "$LOG" \
  --log-level INFO \
  "$@"
echo "Bisync complete. Log: $LOG"
