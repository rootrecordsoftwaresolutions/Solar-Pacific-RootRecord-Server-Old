#!/usr/bin/env bash
# Deploy aws/ bin+systemd to rr-aws and enable the full stack (admin SSH only).
set -euo pipefail
SKILL="$HOME/.ollama/skills/rootrecord-aws"
HOST="${RR_SSH_HOST:-rr-aws}"

chmod +x "$SKILL"/aws/bin/*.py "$SKILL"/aws/bin/*.sh 2>/dev/null || true
chmod +x "$SKILL"/local/*.sh "$SKILL"/local/*.py 2>/dev/null || true

rsync -az --delete \
  --exclude '__pycache__' \
  "$SKILL/aws/bin/" "$HOST:/home/ubuntu/rootrecord/bin/"

rsync -az \
  "$SKILL/aws/systemd/" "$HOST:/home/ubuntu/rootrecord/systemd/"

rsync -az \
  "$SKILL/aws/etc/secrets.env.example" "$HOST:/home/ubuntu/rootrecord/etc/secrets.env.example"

ssh "$HOST" 'bash -s' <<'REMOTE'
set -euo pipefail
ROOT=/home/ubuntu/rootrecord
chmod +x "$ROOT"/bin/*.py "$ROOT"/bin/*.sh

# Chronological drop-in layout (FileZilla / SFTP targets)
mkdir -p "$ROOT"/chronological/{always-on,since-last-fire,on-time,assets} \
         "$ROOT"/radio/{media,fallback,chimes} \
         "$ROOT"/work/{weather,earthquakes,radar,hurricane,noaa,chatlogs,triggers,audio,sysmon,assets} \
         "$ROOT"/logs "$ROOT"/out "$ROOT"/etc
for d in always-on since-last-fire on-time assets; do
  f="$ROOT/chronological/$d/README.txt"
  if [[ ! -f "$f" ]]; then
    cat > "$f" <<EOF
Drop files here ($d).
- *.py pollers auto-run after each pack restart (no manual commands).
- Non-.py (and not Current*) under chronological/ are packed automatically.
- Music beds belong in radio/media/ (not wiped).
EOF
  fi
done

# Packages
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ffmpeg icecast2 vsftpd openssl curl >/tmp/rr-media-apt.log 2>&1 || {
  tail -40 /tmp/rr-media-apt.log; exit 1;
}
sudo systemctl disable --now icecast2 2>/dev/null || true

if [[ ! -f "$ROOT/etc/secrets.env" ]]; then
  cp "$ROOT/etc/secrets.env.example" "$ROOT/etc/secrets.env"
  chmod 600 "$ROOT/etc/secrets.env"
fi
grep -q '^RR_YOUTUBE_RTMP_URL=' "$ROOT/etc/secrets.env" 2>/dev/null || \
  echo 'RR_YOUTUBE_RTMP_URL=' >> "$ROOT/etc/secrets.env"
grep -q '^RR_CLOUDFLARED_TOKEN=' "$ROOT/etc/secrets.env" 2>/dev/null || \
  echo 'RR_CLOUDFLARED_TOKEN=' >> "$ROOT/etc/secrets.env"

bash "$ROOT/bin/install_icecast_config.sh"
bash "$ROOT/bin/install_cloudflared.sh"
bash "$ROOT/bin/install_ftp_filezilla.sh"

sudo cp "$ROOT"/systemd/rr-*.service /etc/systemd/system/
sudo systemctl daemon-reload

sudo systemctl enable --now \
  rr-weather.service rr-earthquake.service rr-radar.service \
  rr-hurricane.service rr-noaa.service \
  rr-chat.service rr-packer.service rr-audio-recv.service \
  rr-dropins.service \
  rr-icecast.service rr-radio.service rr-youtube.service \
  rr-cloudflared.service

sleep 4
echo '=== active ==='
systemctl is-active \
  rr-weather rr-earthquake rr-radar rr-hurricane rr-noaa \
  rr-packer rr-dropins rr-icecast rr-radio rr-youtube \
  rr-audio-recv rr-cloudflared vsftpd || true

"$ROOT/venv/bin/python" - <<'PY'
import sys
sys.path.insert(0, "/home/ubuntu/rootrecord/bin")
from weather_poll import poll_once
from earthquake_poll import poll_once as eq
from radar_poll import poll_once as rd
from hurricane_poll import poll_once as hz
from noaa_poll import poll_once as noaa
from collect_sysmon import collect
from dropin_supervisor import ensure_layout, sync_assets
print("weather", poll_once().get("ok"))
print("earthquake", eq().get("ok"))
print("radar", rd().get("ok"))
print("hurricane", hz().get("ok"), "storms", hz().get("nhc_storm_count"))
print("noaa", noaa().get("ok"), "periods", noaa().get("forecast_periods"))
print("sysmon", collect().get("ok"), "logs", collect().get("logs"))
ensure_layout()
print("assets_synced", sync_assets())
PY

curl -s -o /dev/null -w 'icecast_http=%{http_code}\n' http://127.0.0.1:8000/ || echo icecast_down
# Capture quick-tunnel URL if present
sleep 2
if [[ -f "$ROOT/logs/cloudflared.log" ]]; then
  grep -Eo 'https://[a-zA-Z0-9.-]+\.trycloudflare\.com' "$ROOT/logs/cloudflared.log" | tail -1 \
    | tee "$ROOT/etc/radio-public.url" || true
fi
echo "ftp_password_file=$ROOT/etc/ftp.password"
echo DEPLOY_OK
REMOTE
