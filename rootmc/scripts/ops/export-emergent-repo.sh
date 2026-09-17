#!/usr/bin/env bash
# Sync ALL website / web-app / Worker surfaces into emergent-repo for Emergent AI.
# Covers RootMC, Ava, RootRecord (live OptiPlex trees + archived D: web dumps).
# Excludes node_modules, build artifacts, secrets. Does not commit or push.
set -euo pipefail

AVA_ROOT="${AVA_ROOT:-/home/ava-core/ava}"
ROOTMC="${ROOTMC_WORKSPACE:-$AVA_ROOT/workstations/rootmc}"
WEB="$ROOTMC/Web Files"
CF="${CF_WORKSPACE:-$AVA_ROOT/workstations/cloudflare}"
PROJECTS="${PROJECTS_WORKSPACE:-$AVA_ROOT/workstations/projects}"
ROOTRECORD_WS="${ROOTRECORD_WORKSPACE:-$AVA_ROOT/workstations/rootrecord}"
ARCHIVE="${WEB_FILES_ARCHIVE:-$AVA_ROOT/mirrors/web-files-repo}"
REPO="${EMERGENT_REPO:-$ROOTMC/emergent-repo}"

EXCLUDE=(
  --exclude=node_modules/
  --exclude=.git/
  --exclude=build/
  --exclude=dist/
  --exclude=.next/
  --exclude=.vercel/
  --exclude=.wrangler/
  --exclude=.gradle/
  --exclude=app/build/
  --exclude=.idea/
  --exclude=.env
  --exclude=credentials.env
  --exclude=local.properties
  --exclude=google-services.json
  --exclude='*.jks'
  --exclude='*.keystore'
  --exclude=_deploy-last.log
  --exclude=deploy-out.txt
  --exclude='tmp-*.json'
  --exclude='*.env'
  --exclude=_worker.bundle
)

rsync_tree() {
  local src="$1" dest="$2"
  if [[ ! -d "$src" ]]; then
    echo "skip missing: $src" >&2
    return 0
  fi
  mkdir -p "$dest"
  rsync -a --delete "${EXCLUDE[@]}" "$src"/ "$dest"/
  echo "synced $(basename "$src") → ${dest#"$REPO/"}"
}

echo "Exporting ALL website files → $REPO"

# ── RootMC (live Web Files) ──────────────────────────────────────────────
rsync_tree "$WEB/rootmc-web" "$REPO/web"
rsync_tree "$WEB/rootmc-api" "$REPO/api/rootmc-api"
rsync_tree "$WEB/rootmc-realm-api" "$REPO/api/rootmc-realm-api"
rsync_tree "$WEB/rootmc-api-g2" "$REPO/api/rootmc-api-g2"

rsync_tree "$WEB/rootmc-ava-edge" "$REPO/edge/rootmc-ava-edge"
rsync_tree "$WEB/rootmc-webstat-proxy" "$REPO/edge/rootmc-webstat-proxy"
rsync_tree "$WEB/rootmc-minecraft-map" "$REPO/edge/rootmc-minecraft-map"

rsync_tree "$WEB/rootmc-app" "$REPO/apps/rootmc-app"
rsync_tree "$WEB/rootmc-ava" "$REPO/apps/rootmc-ava"
rsync_tree "$WEB/rootmc-sexi" "$REPO/apps/rootmc-sexi"
rsync_tree "$WEB/rootmc-ava-desktop" "$REPO/apps/rootmc-ava-desktop"

if [[ -d "$ROOTMC/Mobile App Files/rootmc-android" ]]; then
  rsync_tree "$ROOTMC/Mobile App Files/rootmc-android" "$REPO/android"
fi

# ── RootRecord Cloudflare Workers (live preferred) ───────────────────────
# Prefer live CF copies; fall back to Web Files / archive for missing shards.
sync_cf() {
  local name="$1"
  if [[ -d "$CF/$name" ]]; then
    rsync_tree "$CF/$name" "$REPO/api/$name"
  elif [[ -d "$WEB/$name" ]]; then
    rsync_tree "$WEB/$name" "$REPO/api/$name"
  elif [[ -d "$ARCHIVE/rootrecord-web-pre-august/cloudflare/$name" ]]; then
    rsync_tree "$ARCHIVE/rootrecord-web-pre-august/cloudflare/$name" "$REPO/api/$name"
  else
    echo "skip missing CF package: $name" >&2
  fi
}

if [[ -d "$CF/shared" ]]; then
  rsync_tree "$CF/shared" "$REPO/api/shared"
elif [[ -d "$WEB/shared" ]]; then
  rsync_tree "$WEB/shared" "$REPO/api/shared"
fi

for pkg in \
  rootrecord-api-account \
  rootrecord-api-business \
  rootrecord-api-goals \
  rootrecord-api-kilauea \
  rootrecord-api-token \
  rootrecord-api-weather \
  rootrecord-license \
  rootrecord-minecraft-map \
  rootrecord-primary \
  rootrecord-solana-tx \
  rr-weather-manager-api \
  rootrecord-app-build
do
  sync_cf "$pkg"
done

# ── Ava / RootRecord wiki + merged surfaces ──────────────────────────────
rsync_tree "$PROJECTS/rootrecord-ava" "$REPO/sites/rootrecord-ava"
rsync_tree "$PROJECTS/rootrecord-merged" "$REPO/sites/rootrecord-merged"

# ── RootRecord marketing / Solana site (live) ────────────────────────────
rsync_tree "$ROOTRECORD_WS/solana-rootrecord-site" "$REPO/sites/rootrecord-solana"
# Staged Powered-by Ava patches from projects pointer
if [[ -d "$PROJECTS/rootrecord-site" ]]; then
  mkdir -p "$REPO/sites/rootrecord-site-staged"
  rsync -a "${EXCLUDE[@]}" "$PROJECTS/rootrecord-site"/ "$REPO/sites/rootrecord-site-staged"/
  echo "synced rootrecord-site staged → sites/rootrecord-site-staged"
fi

# ── RootRecord.info main site + product web apps (archived D: dump) ──────
# Live copies of these product sites are not on OptiPlex outside this archive.
RR_ARCH="$ARCHIVE/rootrecord-web-pre-august"
if [[ -d "$RR_ARCH/main" ]]; then
  rsync_tree "$RR_ARCH/main" "$REPO/sites/rootrecord-main"
fi
if [[ -d "$RR_ARCH/apps" ]]; then
  for app in "$RR_ARCH/apps"/*; do
    [[ -d "$app" ]] || continue
    base="$(basename "$app")"
    [[ "$base" == "shared" ]] && { rsync_tree "$app" "$REPO/apps/rootrecord-shared"; continue; }
    rsync_tree "$app" "$REPO/apps/$base"
  done
fi
if [[ -d "$RR_ARCH/solana/HELE" ]]; then
  rsync_tree "$RR_ARCH/solana/HELE" "$REPO/sites/hele"
fi
if [[ -d "$RR_ARCH/tools/custodial-wallet-manager" ]]; then
  rsync_tree "$RR_ARCH/tools/custodial-wallet-manager" "$REPO/tools/custodial-wallet-manager"
fi

# ── Full archive snapshots (diff / merge reference) ──────────────────────
if [[ -d "$ARCHIVE/rootmc-web-files-pre-august" ]]; then
  rsync_tree "$ARCHIVE/rootmc-web-files-pre-august" "$REPO/archive/rootmc-web-files-pre-august"
fi
if [[ -d "$ARCHIVE/rootrecord-web-pre-august" ]]; then
  rsync_tree "$ARCHIVE/rootrecord-web-pre-august" "$REPO/archive/rootrecord-web-pre-august"
fi
# Dated RootMC dump only if present and distinct (optional bulk)
if [[ -d "$ARCHIVE/rootmc-web-files-08052026" ]]; then
  rsync_tree "$ARCHIVE/rootmc-web-files-08052026" "$REPO/archive/rootmc-web-files-08052026"
fi

# Drop noise from Emergent surface
rm -rf "$REPO/web-files-halted" "$REPO/_deploy-logs" 2>/dev/null || true

# Keep a copy of the exporter inside the repo
mkdir -p "$REPO/scripts"
cp -f "$ROOTMC/scripts/export-emergent-repo.sh" "$REPO/scripts/export-emergent-repo.sh"
chmod +x "$REPO/scripts/export-emergent-repo.sh"

files=$(find "$REPO" -type f ! -path '*/.git/*' ! -path '*/node_modules/*' | wc -l)
echo "Done. $files files under $REPO"
echo "Next: commit + push Ava-Core-Dev/rootmc-emergent for Emergent."
