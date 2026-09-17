#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="${AVA_INSTALL_DIR:-$SCRIPT_DIR}"
AVA_USER="${AVA_USER:-ava}"
LOG_DIR="${AVA_LOG_DIR:-/var/log/ava-server}"
LOG_FILE="$LOG_DIR/first-run-install.log"
REPO_URL="${AVA_SERVER_REPO:-https://github.com/Ava-Core-Dev/RootRecord-Core-Processor.git}"

if [[ "$(id -u)" -ne 0 ]]; then
  exec sudo -E bash "$0" "$@"
fi

mkdir -p "$LOG_DIR"
exec > >(tee -a "$LOG_FILE") 2>&1

log() { printf '[%s] %s\n' "$(date -Is)" "$*"; }
fail() { log "ERROR: $*"; exit 1; }
trap 'fail "line $LINENO: $BASH_COMMAND"' ERR

log "AVA server first-run installer starting"
log "installer=$SCRIPT_DIR"
log "install_dir=$INSTALL_DIR"
log "log_file=$LOG_FILE"

command -v apt-get >/dev/null 2>&1 || fail "This installer currently supports Debian/Ubuntu systems with apt-get."
source /etc/os-release
log "host=${PRETTY_NAME:-unknown} arch=$(dpkg --print-architecture)"

export DEBIAN_FRONTEND=noninteractive
log "Updating apt package metadata"
apt-get update

PACKAGES=(
  ca-certificates
  curl
  git
  openssh-client
  python3
  python3-venv
  python3-pip
  python3-dev
  build-essential
  ffmpeg
  sqlite3
  jq
  rsync
  libffi-dev
  libssl-dev
  pkg-config
)
log "Installing OS dependencies: ${PACKAGES[*]}"
apt-get install -y --no-install-recommends "${PACKAGES[@]}"

if ! id -u "$AVA_USER" >/dev/null 2>&1; then
  log "Creating unprivileged service user: $AVA_USER"
  useradd --system --create-home --shell /usr/sbin/nologin "$AVA_USER"
else
  log "Service user already exists: $AVA_USER"
fi

mkdir -p "$INSTALL_DIR" "$INSTALL_DIR/data/db" "$INSTALL_DIR/data/logs" "$INSTALL_DIR/Media" "$INSTALL_DIR/config" "$INSTALL_DIR/secrets"
chown -R "$AVA_USER:$AVA_USER" "$INSTALL_DIR/data" "$INSTALL_DIR/Media" "$INSTALL_DIR/config" "$INSTALL_DIR/secrets"
chmod 700 "$INSTALL_DIR/secrets"

if [[ -f "$SCRIPT_DIR/pyproject.toml" ]]; then
  log "Creating Python virtual environment"
  if [[ ! -x "$INSTALL_DIR/.venv/bin/python" ]]; then
    python3 -m venv "$INSTALL_DIR/.venv"
  fi
  log "Installing Python dependencies from pyproject.toml"
  "$INSTALL_DIR/.venv/bin/python" -m pip install --upgrade pip wheel
  "$INSTALL_DIR/.venv/bin/python" -m pip install -e "$INSTALL_DIR"
else
  log "No pyproject.toml yet; Python dependency installation will run when AVA Core is added."
fi

if [[ -f "$SCRIPT_DIR/core/boot.py" ]]; then
  log "Running central Processor boot self-check"
  python3 "$SCRIPT_DIR/core/boot.py"
fi

if [[ -f "$SCRIPT_DIR/.env.example" && ! -f "$INSTALL_DIR/.env" ]]; then
  log "Creating blank .env from .env.example; secrets remain operator-managed"
  install -o "$AVA_USER" -g "$AVA_USER" -m 600 "$SCRIPT_DIR/.env.example" "$INSTALL_DIR/.env"
fi

if [[ -f "$SCRIPT_DIR/systemd/ava-github-pull.service" && -f "$SCRIPT_DIR/systemd/ava-github-pull.timer" ]]; then
  log "Installing GitHub pull service and timer"
  install -o root -g root -m 0644 "$SCRIPT_DIR/systemd/ava-github-pull.service" /etc/systemd/system/ava-github-pull.service
  install -o root -g root -m 0644 "$SCRIPT_DIR/systemd/ava-github-pull.timer" /etc/systemd/system/ava-github-pull.timer
  systemctl daemon-reload
  systemctl enable ava-github-pull.timer
  log "GitHub pull timer enabled; it will start after repository authentication is configured"
else
  log "GitHub pull unit files are not present yet; skipping systemd installation"
fi

log "Dependency versions"
python3 --version
python3 -m pip --version
git --version
ffmpeg -version | sed -n '1p'
sqlite3 --version

log "INSTALL_COMPLETE"
log "Next: configure read-only GitHub SSH access, then run: systemctl start ava-github-pull.service"
log "Full log remains available at $LOG_FILE"
