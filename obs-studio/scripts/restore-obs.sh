#!/usr/bin/env bash
# Restore OBS layouts/settings/scripts after a snap refresh or reinstall.
# Usage: restore-obs.sh [--yes] [snapshot-dir]
set -euo pipefail

SNAP_COMMON="${HOME}/snap/obs-studio/common/.config/obs-studio"
MEDIA_ROOT="${HOME}/Media/public/stream/obs-backup"
SSD_ROOT="${HOME}/.ollama/skills/database/store/obs-backup"
YES=0
SRC=""

log() { printf '%s %s\n' "$(date -Iseconds)" "$*"; }

for arg in "$@"; do
  case "${arg}" in
    --yes|-y) YES=1 ;;
    *) SRC="${arg}" ;;
  esac
done

if [[ -z "${SRC}" ]]; then
  if [[ -d "${MEDIA_ROOT}/latest/obs-studio/basic" ]]; then
    SRC="${MEDIA_ROOT}/latest"
  elif [[ -d "${SSD_ROOT}/latest/obs-studio/basic" ]]; then
    SRC="${SSD_ROOT}/latest"
  fi
fi

if [[ -z "${SRC}" || ! -d "${SRC}/obs-studio/basic" ]]; then
  echo "No OBS backup found. Looked at ${MEDIA_ROOT}/latest and ${SSD_ROOT}/latest" >&2
  exit 1
fi

if [[ "${YES}" -ne 1 ]]; then
  echo "Restore OBS config from: ${SRC}"
  echo "Into: ${SNAP_COMMON}"
  echo "OBS should be closed. Re-run with --yes to apply."
  exit 2
fi

if pgrep -x obs >/dev/null 2>&1; then
  log "stopping OBS so files are not overwritten on exit"
  pkill -x obs || true
  for _ in 1 2 3 4 5 6 7 8; do
    pgrep -x obs >/dev/null 2>&1 || break
    sleep 0.5
  done
fi

mkdir -p "${SNAP_COMMON}"
rsync -a "${SRC}/obs-studio/" "${SNAP_COMMON}/"
log "restored studio config"

if [[ -d "${SRC}/overlays" ]]; then
  mkdir -p "${HOME}/Media/public/stream/overlays"
  rsync -a "${SRC}/overlays/" "${HOME}/Media/public/stream/overlays/"
  log "restored overlays"
fi
if [[ -d "${SRC}/obs-cams" ]]; then
  mkdir -p "${HOME}/Media/public/stream/obs-cams"
  rsync -a "${SRC}/obs-cams/" "${HOME}/Media/public/stream/obs-cams/"
  log "restored obs-cams"
fi
if [[ -d "${SRC}/scripts/workstations-obs" ]]; then
  mkdir -p "${HOME}/RootRecord/Ava-Core/workstations/obs"
  rsync -a "${SRC}/scripts/workstations-obs/" "${HOME}/RootRecord/Ava-Core/workstations/obs/"
  log "restored workstation OBS scripts"
fi

log "restore done — start OBS from companions or: obs-studio --disable-missing-files-check"
exit 0
