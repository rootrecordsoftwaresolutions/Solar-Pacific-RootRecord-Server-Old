#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOUNT="$DIR/mnt"
mkdir -p "$MOUNT"
fusermount -uz "$MOUNT" 2>/dev/null || true
rclone --config "$DIR/config/rclone.conf" mount ec2:/home/ubuntu "$MOUNT" \
  --vfs-cache-mode full \
  --vfs-cache-max-age 1h \
  --dir-cache-time 5m \
  --poll-interval 1m \
  --daemon \
  --log-file "$DIR/logs/mount.log" \
  --log-level INFO
echo "Mounted EC2 /home/ubuntu at $MOUNT"
