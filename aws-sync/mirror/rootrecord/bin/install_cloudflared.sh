#!/usr/bin/env bash
# Install cloudflared on RootRecord AWS (radio public URL).
set -euo pipefail
if command -v cloudflared >/dev/null 2>&1; then
  cloudflared --version
  exit 0
fi
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64|amd64) DEB_ARCH=amd64 ;;
  aarch64|arm64) DEB_ARCH=arm64 ;;
  *) echo "unsupported arch $ARCH"; exit 1 ;;
esac
TMP="$(mktemp -d)"
cd "$TMP"
curl -fsSL -o cloudflared.deb \
  "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${DEB_ARCH}.deb"
sudo dpkg -i cloudflared.deb
cloudflared --version
rm -rf "$TMP"
