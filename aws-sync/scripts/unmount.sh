#!/usr/bin/env bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fusermount -uz "$DIR/mnt" 2>/dev/null || umount "$DIR/mnt" 2>/dev/null || true
echo "Unmounted."
