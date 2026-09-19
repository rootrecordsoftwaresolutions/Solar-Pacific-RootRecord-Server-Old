#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
rclone --config "$DIR/config/rclone.conf" bisync \
  ec2:/home/ubuntu \
  "$DIR/mirror" \
  --resync \
  --create-empty-src-dirs \
  --compare size,modtime,checksum \
  --log-file "$DIR/logs/initial-resync.log" \
  --log-level INFO
echo "Initial resync done."
