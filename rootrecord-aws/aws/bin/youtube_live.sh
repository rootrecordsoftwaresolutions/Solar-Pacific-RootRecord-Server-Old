#!/usr/bin/env bash
# RootRecord YouTube livestream — slides + radio audio → RTMP.
# Idle until RR_YOUTUBE_RTMP_URL is set in etc/secrets.env
# Example: RR_YOUTUBE_RTMP_URL=rtmp://a.rtmp.youtube.com/live2/xxxx-xxxx
set -euo pipefail
ROOT="${RR_ROOT:-/home/ubuntu/rootrecord}"
ETC="$ROOT/etc"
LOG="$ROOT/logs/youtube.log"
SECRETS="$ETC/secrets.env"
SLIDE="$ROOT/radio/Current-slide.png"
ICE_HOST="${RR_ICE_HOST:-127.0.0.1}"
ICE_PORT="${RR_ICE_PORT:-8000}"
MOUNT="${RR_ICE_MOUNT:-/rootrecord.mp3}"

mkdir -p "$ROOT/radio" "$ROOT/logs"

if [[ -f "$SECRETS" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$SECRETS"
  set +a
fi

RTMP="${RR_YOUTUBE_RTMP_URL:-}"
if [[ -z "$RTMP" ]]; then
  echo "$(date -Iseconds) youtube idle — set RR_YOUTUBE_RTMP_URL in $SECRETS" >&2
  while true; do sleep 60; done
fi

# Prefer radar Current.gif as motion slide; else generate brand card
RADAR="$ROOT/work/radar/Current.gif"
if [[ -f "$RADAR" ]]; then
  ffmpeg -y -hide_banner -loglevel error -i "$RADAR" -frames:v 1 "$SLIDE" || true
fi
if [[ ! -f "$SLIDE" ]]; then
  ffmpeg -y -hide_banner -loglevel error \
    -f lavfi -i "color=c=0x0b1f2a:s=1280x720:d=1" \
    -vf "drawtext=text='Root Record':fontsize=64:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-40,drawtext=text='Live':fontsize=36:fontcolor=0x7ec8e3:x=(w-text_w)/2:y=(h-text_h)/2+40" \
    -frames:v 1 "$SLIDE"
fi

AUDIO_URL="http://${ICE_HOST}:${ICE_PORT}${MOUNT}"
echo "$(date -Iseconds) youtube starting slide=$SLIDE audio=$AUDIO_URL" >&2

# Lean encode for t3.micro — may need instance upsize for stable 720p
exec ffmpeg -hide_banner -loglevel warning -re \
  -loop 1 -framerate 2 -i "$SLIDE" \
  -i "$AUDIO_URL" \
  -c:v libx264 -preset ultrafast -tune stillimage -b:v 800k -pix_fmt yuv420p -g 60 \
  -c:a aac -b:a 128k -ar 44100 -ac 2 \
  -shortest -f flv "$RTMP"
