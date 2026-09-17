#!/usr/bin/env bash
# Copy all Work Stations .env / credentials onto the Ubuntu SSD device.
# SOURCE on SATA/E is NEVER modified (cp only).
#
# Usage:
#   sudo bash sync-env-to-device.sh
#
# Env:
#   WORKSTATIONS=/mnt/e/.1\ Work\ Stations
#   DEVICE_ROOT=/srv/rootmc
#   PROVISION_USER=ubuntu
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

WORKSTATIONS="${WORKSTATIONS:-/mnt/e/.1 Work Stations}"
DEVICE_ROOT="${DEVICE_ROOT:-/srv/rootmc}"
PROVISION_USER="${PROVISION_USER:-${SUDO_USER:-ubuntu}}"
SECRETS_DIR="$DEVICE_ROOT/secrets"
LOG="$DEVICE_ROOT/secrets-sync.log"

if [[ ! -d "$WORKSTATIONS" ]]; then
  echo "WORKSTATIONS missing: $WORKSTATIONS" >&2
  exit 1
fi

mkdir -p "$DEVICE_ROOT" "$SECRETS_DIR"
: >"$LOG"
log() { echo "$*" | tee -a "$LOG"; }

log "sync-env-to-device $(date -Iseconds)"
log "source=$WORKSTATIONS (intact)"
log "device=$DEVICE_ROOT"

# Collect env-like files (relative paths)
mapfile -t FILES < <(
  find "$WORKSTATIONS" -type f \( \
      -name '.env' -o -name '.env.*' -o -name '*.env' -o -name 'solana.env' \
    \) \
    ! -path '*/node_modules/*' \
    ! -path '*/.git/*' \
    ! -path '*/halted-development/*' \
    ! -name '*.env.example' \
    2>/dev/null | sort
)

copied=0
skipped=0
for abs in "${FILES[@]}"; do
  rel="${abs#"$WORKSTATIONS"/}"
  # Skip pure examples
  if [[ "$rel" == *'.env.example' ]]; then
    skipped=$((skipped + 1))
    continue
  fi
  dest="$SECRETS_DIR/$rel"
  mkdir -p "$(dirname "$dest")"
  cp -f --preserve=timestamps "$abs" "$dest"
  chmod 600 "$dest"
  copied=$((copied + 1))
  log "OK $rel ($(wc -c <"$dest") bytes)"
done

# Primary runtime copies (convenience paths)
PRIMARY="$WORKSTATIONS/RootMC/.env"
CREDS="$WORKSTATIONS/.credentials/.env"
SOLANA="$WORKSTATIONS/.credentials/solana.env"

if [[ -f "$PRIMARY" ]]; then
  cp -f --preserve=timestamps "$PRIMARY" "$DEVICE_ROOT/.env"
  chmod 600 "$DEVICE_ROOT/.env"
  log "PRIMARY -> $DEVICE_ROOT/.env"
fi
if [[ -f "$CREDS" ]]; then
  cp -f --preserve=timestamps "$CREDS" "$DEVICE_ROOT/.credentials.env"
  chmod 600 "$DEVICE_ROOT/.credentials.env"
  log "CREDS -> $DEVICE_ROOT/.credentials.env"
fi
if [[ -f "$SOLANA" ]]; then
  cp -f --preserve=timestamps "$SOLANA" "$DEVICE_ROOT/.credentials.solana.env"
  chmod 600 "$DEVICE_ROOT/.credentials.solana.env"
  log "SOLANA -> $DEVICE_ROOT/.credentials.solana.env"
fi

# Also ensure live RootMC tree on E keeps working if bind-mounted:
# if DEVICE_ROOT is a bind of Work Stations RootMC, primary already exists.
# Drop a pointer for loaders.
cat >"$DEVICE_ROOT/ENV-LOCATION.txt" <<EOF
Primary runtime: $DEVICE_ROOT/.env
Full mirror:     $SECRETS_DIR/
Source (intact): $WORKSTATIONS/
Synced:          $(date -Iseconds)
Set: ROOTMC_ENV_FILE=$DEVICE_ROOT/.env
EOF
chmod 644 "$DEVICE_ROOT/ENV-LOCATION.txt"

chown -R "$PROVISION_USER:$PROVISION_USER" "$SECRETS_DIR" "$DEVICE_ROOT/.env" \
  "$DEVICE_ROOT/.credentials.env" "$DEVICE_ROOT/.credentials.solana.env" \
  "$DEVICE_ROOT/ENV-LOCATION.txt" "$LOG" 2>/dev/null || true
chmod 700 "$SECRETS_DIR"

log "DONE copied=$copied skipped_example=$skipped"
log "Source on E/SATA was not moved or deleted."
echo ""
echo "Verify sizes (no secret dump):"
echo "  wc -c \"$DEVICE_ROOT/.env\" \"$WORKSTATIONS/RootMC/.env\""
echo "  find \"$SECRETS_DIR\" -type f | wc -l"
