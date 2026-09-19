#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
mkdir -p data
if [ "$(id -u)" -ne 0 ]; then
  exec sudo -E bash "$0" "$@"
fi
exec node server.js
