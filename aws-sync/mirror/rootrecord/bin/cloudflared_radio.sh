#!/usr/bin/env bash
set -euo pipefail
ROOT="${RR_ROOT:-/home/ubuntu/rootrecord}"
SECRETS="$ROOT/etc/secrets.env"
mkdir -p "$ROOT/logs" "$ROOT/etc"
[[ -f "$SECRETS" ]] && { set -a; source "$SECRETS" || true; set +a; }
BIN="$(command -v cloudflared || true)"
[[ -z "$BIN" && -x /usr/local/bin/cloudflared ]] && BIN=/usr/local/bin/cloudflared
[[ -n "${RR_CLOUDFLARED_TOKEN:-}" ]] && exec "$BIN" tunnel --no-autoupdate run --token "$RR_CLOUDFLARED_TOKEN"
exec "$BIN" tunnel --no-autoupdate --url http://127.0.0.1:8088
