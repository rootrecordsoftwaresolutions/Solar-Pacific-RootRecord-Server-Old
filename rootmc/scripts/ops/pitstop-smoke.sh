#!/usr/bin/env bash
# Quick health check for Ubuntu pit-stop (E mounted, D may be gone).
# Does not print secret values.
set -euo pipefail

ROOT="${ROOTMC_WORKSPACE:-/mnt/e/.1 Work Stations/RootMC}"
if [[ ! -d "$ROOT" ]]; then
  # Fall back to layout-relative when invoked from the tree
  HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  ROOT="$(cd "${HERE}/.." && pwd)"
fi

AVA="$ROOT/Web Files/rootmc-ava"
fail=0

ok() { echo "OK  $*"; }
bad() { echo "FAIL $*"; fail=1; }

echo "pitstop-smoke · ROOT=$ROOT"

if [[ -f "$ROOT/.env" ]]; then ok ".env present (not reading values)"; else bad ".env missing"; fi
if [[ -f "$ROOT/scripts/load-rootmc-env.sh" ]]; then ok "load-rootmc-env.sh"; else bad "load-rootmc-env.sh missing"; fi

if command -v node >/dev/null 2>&1; then
  ok "node $(node -v)"
  major="$(node -v | sed 's/^v//;s/\..*//')"
  if [[ "$major" -lt 20 ]]; then bad "need Node >= 20"; fi
else
  bad "node not installed — run ubuntu-provision-ecosystem.sh"
fi

if [[ -f "$AVA/package.json" ]]; then ok "rootmc-ava package.json"; else bad "rootmc-ava missing"; fi

if [[ -d "$AVA/node_modules" ]]; then
  ok "node_modules present"
else
  echo "INFO npm install needed in rootmc-ava (first boot)"
fi

if [[ -f "$ROOT/Plugin Building/Minecraft/local.properties.linux" ]]; then
  ok "local.properties.linux present"
else
  bad "local.properties.linux missing"
fi

# Soft-check Listening intention — do not start Ava here
if ss -ltn 2>/dev/null | grep -q ':8787'; then
  ok "port 8787 already listening (Ava status?)"
else
  echo "INFO :8787 not listening yet (start ava-ivy when ready)"
fi

if [[ "$fail" -ne 0 ]]; then
  echo "pitstop-smoke FAILED"
  exit 1
fi
echo "pitstop-smoke PASSED"
exit 0
