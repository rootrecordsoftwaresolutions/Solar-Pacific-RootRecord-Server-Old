#!/usr/bin/env bash
# FreeLTC XMRig installer + Idle / Screensaver mode (Linux)
# - Light / paused while you use the PC
# - Full speed when the system is idle (screensaver style)
# Referral: U-80KT6Z

set -euo pipefail

REF="U-80KT6Z"
POOL="rx.unmineable.com:3333"
INSTALL_DIR="${HOME}/FreeLTC-XMRig"
API_PORT=18088
IDLE_THRESHOLD_MS=60000          # 60 seconds of no input → full speed

echo ""
echo "========================================"
echo "  FreeLTC Idle Miner (Linux)"
echo "  Light while active → Full when idle"
echo "  Pool: unMineable | Coin: LTC"
echo "  Referral: $REF (0.75% fee)"
echo "========================================"
echo ""

read -rp "Enter your Litecoin (LTC) wallet address: " LTC
LTC=$(echo "$LTC" | xargs)
if [[ -z "$LTC" ]]; then
  echo "No address entered. Exiting."
  exit 1
fi

read -rp "Worker name (press Enter for 'pc1'): " WORKER
WORKER=${WORKER:-pc1}

mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"

echo ""
echo "Downloading latest XMRig..."

ARCH=$(uname -m)
case "$ARCH" in
  x86_64)  ASSET_PATTERN="xmrig-.*-linux-static-x64\.tar\.gz" ;;
  aarch64|arm64) ASSET_PATTERN="xmrig-.*-linux-static-arm64\.tar\.gz" ;;
  *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac

API="https://api.github.com/repos/xmrig/xmrig/releases/latest"
if command -v curl >/dev/null 2>&1; then
  DOWNLOAD_URL=$(curl -sL -H "User-Agent: FreeLTC-Installer" "$API" \
    | grep -oE '"browser_download_url"[[:space:]]*:[[:space:]]*"[^"]+"' \
    | grep -oE 'https://[^"]+' \
    | grep -E "$ASSET_PATTERN" \
    | head -1)
else
  DOWNLOAD_URL=$(wget -qO- --header="User-Agent: FreeLTC-Installer" "$API" \
    | grep -oE '"browser_download_url"[[:space:]]*:[[:space:]]*"[^"]+"' \
    | grep -oE 'https://[^"]+' \
    | grep -E "$ASSET_PATTERN" \
    | head -1)
fi

if [[ -z "$DOWNLOAD_URL" ]]; then
  echo "Could not find matching XMRig release."
  exit 1
fi

echo "Found: $DOWNLOAD_URL"
TARFILE="xmrig.tar.gz"
if command -v curl >/dev/null 2>&1; then
  curl -L --fail -o "$TARFILE" "$DOWNLOAD_URL"
else
  wget -O "$TARFILE" "$DOWNLOAD_URL"
fi

echo "Extracting..."
tar -xzf "$TARFILE"
rm -f "$TARFILE"

XMRIG_DIR=$(find . -maxdepth 1 -type d -name 'xmrig-*' | head -1)
if [[ -n "$XMRIG_DIR" ]]; then
  mv "$XMRIG_DIR"/* .
  rmdir "$XMRIG_DIR" 2>/dev/null || true
fi

if [[ ! -f ./xmrig ]]; then
  echo "xmrig binary not found. Aborting."
  exit 1
fi
chmod +x ./xmrig

USER_STR="LTC:${LTC}.${WORKER}#${REF}"

# Config with HTTP API enabled so the controller can pause/resume
cat > config.json <<EOF
{
    "api": {
        "id": null,
        "worker-id": null
    },
    "http": {
        "enabled": true,
        "host": "127.0.0.1",
        "port": $API_PORT,
        "access-token": null,
        "restricted": false
    },
    "autosave": true,
    "background": false,
    "colors": true,
    "cpu": {
        "enabled": true,
        "huge-pages": true,
        "max-threads-hint": 100
    },
    "opencl": false,
    "cuda": false,
    "donate-level": 1,
    "pools": [
        {
            "url": "$POOL",
            "user": "$USER_STR",
            "pass": "x",
            "keepalive": true,
            "tls": false
        }
    ]
}
EOF

# ---------- Idle controller ----------
cat > idle-controller.sh << 'CONTROLLER'
#!/usr/bin/env bash
# FreeLTC idle controller
# Paused (almost zero CPU) while you use the PC
# Full speed after IDLE_THRESHOLD_MS of no input

INSTALL_DIR="$(cd "$(dirname "$0")" && pwd)"
API="http://127.0.0.1:18088"
IDLE_THRESHOLD_MS=60000
CHECK_INTERVAL=6

get_idle_ms() {
  if command -v xprintidle >/dev/null 2>&1; then
    xprintidle 2>/dev/null || echo 0
  elif command -v xssstate >/dev/null 2>&1; then
    local s
    s=$(xssstate -i 2>/dev/null || echo 0)
    echo $((s * 1000))
  else
    # No idle tool → stay paused (safe default)
    echo 0
  fi
}

pause_miner() {
  curl -s -X POST "$API/json_rpc" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"pause","id":1}' >/dev/null 2>&1 || true
}

resume_miner() {
  curl -s -X POST "$API/json_rpc" \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","method":"resume","id":1}' >/dev/null 2>&1 || true
}

echo "[idle-controller] started (full speed after ${IDLE_THRESHOLD_MS}ms idle)"
MODE="unknown"

while true; do
  IDLE=$(get_idle_ms)
  if [[ "$IDLE" -ge "$IDLE_THRESHOLD_MS" ]]; then
    if [[ "$MODE" != "full" ]]; then
      echo "[idle-controller] $(date '+%H:%M:%S')  idle → FULL speed"
      resume_miner
      MODE="full"
    fi
  else
    if [[ "$MODE" != "light" ]]; then
      echo "[idle-controller] $(date '+%H:%M:%S')  active → PAUSED (light)"
      pause_miner
      MODE="light"
    fi
  fi
  sleep "$CHECK_INTERVAL"
done
CONTROLLER
chmod +x idle-controller.sh

# Main start script
cat > start.sh << EOF
#!/usr/bin/env bash
cd "\$(dirname "\$0")"

echo "Starting FreeLTC Idle Miner..."
echo "Address : $LTC"
echo "Worker  : $WORKER"
echo "Referral: $REF"
echo ""
echo "Behaviour:"
echo "  • Using the PC     → mining PAUSED (almost zero CPU)"
echo "  • Idle > 60 sec    → FULL mining speed"
echo "API: http://127.0.0.1:$API_PORT"
echo ""

./xmrig --config=config.json &
XMRIG_PID=\$!

sleep 3

./idle-controller.sh &
CTRL_PID=\$!

cleanup() {
  echo ""
  echo "Stopping miner and controller..."
  kill \$CTRL_PID 2>/dev/null || true
  kill \$XMRIG_PID 2>/dev/null || true
  wait 2>/dev/null || true
  exit 0
}
trap cleanup INT TERM

echo "Running. Press Ctrl+C to stop."
wait \$XMRIG_PID
EOF
chmod +x start.sh

cat > stop.sh << 'EOF'
#!/usr/bin/env bash
pkill -f "FreeLTC-XMRig/xmrig" 2>/dev/null || true
pkill -f "idle-controller.sh" 2>/dev/null || true
echo "Stopped."
EOF
chmod +x stop.sh

echo ""
echo "========================================"
echo "  Installation complete!"
echo "========================================"
echo ""
echo "Folder : $INSTALL_DIR"
echo "Start  : cd $INSTALL_DIR && ./start.sh"
echo "Stop   : cd $INSTALL_DIR && ./stop.sh"
echo ""
echo "Behaviour:"
echo "  • While you use the PC  → mining is PAUSED (near zero CPU)"
echo "  • After ~60 s of idle  → full mining speed (screensaver style)"
echo ""
echo "Optional (better hashrate):"
echo "  sudo sysctl -w vm.nr_hugepages=1280"
echo ""
echo "Check balance: https://unmineable.com  (search your LTC address)"
echo ""

if ! command -v xprintidle >/dev/null 2>&1 && ! command -v xssstate >/dev/null 2>&1; then
  echo "NOTE: For proper idle detection install one of these:"
  echo "  sudo apt install xprintidle"
  echo "  # or"
  echo "  sudo apt install xssstate"
  echo "(Without them the miner stays paused)"
  echo ""
fi

read -rp "Start the idle miner now? [Y/n] " RUN
RUN=${RUN:-Y}
if [[ "$RUN" =~ ^[Yy]$ ]]; then
  ./start.sh
fi
