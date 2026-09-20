#!/usr/bin/env bash
# Copy council trust.json only. Never copies secrets.env.
set -euo pipefail
SRC="${HOME}/.config/ava-council/trust.json"
DEST_DIR="${HOME}/Documents/ava-council-backups"
mkdir -p "${DEST_DIR}"
if [[ ! -f "${SRC}" ]]; then
  echo "no trust.json at ${SRC}" >&2
  exit 0
fi
STAMP="$(date -Iseconds | tr ':' '-')"
cp -a "${SRC}" "${DEST_DIR}/trust-${STAMP}.json"
echo "wrote ${DEST_DIR}/trust-${STAMP}.json"
