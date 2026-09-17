#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${ROOT}/.runtime/logs"
mkdir -p "$LOG_DIR"
exec > >(tee -a "$LOG_DIR/install.log") 2>&1
log() { printf '[%s] %s\n' "$(date -Is)" "$*"; }
log "RootRecord Core Node bootstrap start"
if command -v apt-get >/dev/null 2>&1; then
  if [[ "$(id -u)" -ne 0 ]]; then exec sudo -E bash "$0" "$@"; fi
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y --no-install-recommends ca-certificates curl git python3 python3-venv python3-pip build-essential
fi
python3 "$ROOT/core/boot.py"
log "NODE_INSTALL_COMPLETE"
log "Logs: $LOG_DIR/install.log and $LOG_DIR/boot.log"
