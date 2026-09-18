#!/usr/bin/env bash
# Cloudflare quick tunnel (or named token) for RootRecord radio Icecast.
set -euo pipefail
ROOT="${RR_ROOT:-/home/ubuntu/rootrecord}"
SECRETS="$ROOT/etc/secrets.env"
mkdir -p "$ROOT/logs" "$ROOT/etc"

if [[ -f "$SECRETS" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$SECRETS" || true
  set +a
fi

BIN="$(command -v cloudflared || true)"
if [[ -z "$BIN" && -x /usr/local/bin/cloudflared ]]; then BIN=/usr/local/bin/cloudflared; fi
if [[ -z "$BIN" ]]; then
  echo "cloudflared missing" >&2
  exit 1
fi

# Named tunnel token preferred (permanent hostname)
if [[ -n "${RR_CLOUDFLARED_TOKEN:-}" ]]; then
  echo "$(date -Iseconds) cloudflared named tunnel" >&2
  exec "$BIN" tunnel --no-autoupdate run --token "$RR_CLOUDFLARED_TOKEN"
fi

# Ephemeral quick tunnel. URL is scraped from logs into etc/radio-public.url by collect_sysmon / deploy.
echo "$(date -Iseconds) cloudflared quick tunnel → http://127.0.0.1:8000" >&2
exec "$BIN" tunnel --no-autoupdate --url http://127.0.0.1:8000
