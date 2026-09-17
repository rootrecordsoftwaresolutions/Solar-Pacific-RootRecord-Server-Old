#!/usr/bin/env bash
# Bash twin of load-rootmc-env.ps1 for Ubuntu pit-stop / Remote-SSH.
# Usage (from any script):
#   # shellcheck source=/dev/null
#   source "/mnt/e/.1 Work Stations/RootMC/scripts/load-rootmc-env.sh"
#
# Prefer layout-relative .env, then E mount credentials. Never echo secret values.
# Safe to source (no set -e) so a caller’s shell is not aborted.

_rootmc_import_dotenv() {
  local file="$1"
  local skip_cf="${2:-0}"
  [[ -f "$file" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" != *=* ]] && continue
    local k="${line%%=*}"
    local v="${line#*=}"
    k="${k%"${k##*[![:space:]]}"}"
    v="${v#"${v%%[![:space:]]*}"}"
    v="${v%"${v##*[![:space:]]}"}"
    if [[ "$skip_cf" == "1" && "$k" =~ ^CLOUDFLARE_|^ROOTMC_CLOUDFLARE_ ]]; then
      continue
    fi
    [[ -n "$k" && -n "$v" ]] || continue
    export "$k=$v"
  done < "$file"
}

ROOTMC_SCRIPTS_DIR="/home/rootrecord/.ollama/skills/rootmc/scripts"
ROOTMC_WORKSPACE_ROOT="/home/rootrecord/.ollama/skills/origin"
ROOTMC_PROJECTS_ROOT="/home/rootrecord/.ollama/skills"

# Explicit override wins
if [[ -n "${ROOTMC_ENV_FILE:-}" ]]; then
  _rootmc_import_dotenv "$ROOTMC_ENV_FILE" 0
fi

# Drive / mount credentials (gaps), then workstation, then workspace override
for cand in \
  "/mnt/e/.credentials/.env" \
  "/mnt/e/.1 Work Stations/.credentials/.env" \
  "/srv/rootmc/.credentials/.env" \
  "${HOME:-/root}/.credentials/.env" \
  "${ROOTMC_PROJECTS_ROOT}/.credentials/.env" \
  "${ROOTMC_PROJECTS_ROOT}/credentials.env"
do
  _rootmc_import_dotenv "$cand" 1
done

_rootmc_import_dotenv "${ROOTMC_WORKSPACE_ROOT}/.env" 0

# If ROOTMC_ENV_FILE unset, point Ava/scripts at workspace .env when present
if [[ -z "${ROOTMC_ENV_FILE:-}" && -f "${ROOTMC_WORKSPACE_ROOT}/.env" ]]; then
  export ROOTMC_ENV_FILE="${ROOTMC_WORKSPACE_ROOT}/.env"
fi

unset CLOUDFLARE_API_KEY CLOUDFLARE_EMAIL CLOUDFLARE_GLOBAL_API_KEY 2>/dev/null || true
