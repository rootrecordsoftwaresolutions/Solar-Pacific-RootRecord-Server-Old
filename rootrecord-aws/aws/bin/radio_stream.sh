#!/usr/bin/env bash
# RootRecord Radio — always-on Icecast mount fed by ffmpeg.
# Plays work/audio/Current*.wav when present; otherwise a quiet bed.
set -euo pipefail
ROOT="${RR_ROOT:-/home/ubuntu/rootrecord}"
ETC="$ROOT/etc"
LOG="$ROOT/logs/radio.log"
PASS_FILE="$ETC/radio.source.password"
ICE_HOST="${RR_ICE_HOST:-127.0.0.1}"
ICE_PORT="${RR_ICE_PORT:-8000}"
MOUNT="${RR_ICE_MOUNT:-/rootrecord.mp3}"

mkdir -p "$ROOT/work/audio" "$ROOT/radio/media" "$ROOT/logs" "$ETC"

if [[ ! -f "$PASS_FILE" ]]; then
  openssl rand -hex 12 > "$PASS_FILE"
  chmod 600 "$PASS_FILE"
fi
PASS="$(tr -d '\n' < "$PASS_FILE")"

# Music beds first (radio/media), then optional Current voice cuts
list="$ROOT/radio/Current-playlist.txt"
: > "$list"
shopt -s nullglob globstar
while IFS= read -r -d '' f; do
  printf "file '%s'\n" "$f" >> "$list"
done < <(find "$ROOT/radio/media" -type f \( -iname '*.mp3' -o -iname '*.wav' -o -iname '*.ogg' \) -print0 2>/dev/null | sort -z)
# Voice/report cuts after beds (short inserts when present)
for f in "$ROOT/work/audio"/Current*.wav "$ROOT/work/audio"/current*.wav \
         "$ROOT/work/audio"/Current*.mp3; do
  [[ -f "$f" ]] || continue
  printf "file '%s'\n" "$f" >> "$list"
done
shopt -u nullglob globstar
# de-dupe playlist lines while keeping order
if [[ -s "$list" ]]; then
  awk '!seen[$0]++' "$list" > "${list}.uniq" && mv "${list}.uniq" "$list"
fi

ICE_URL="icecast://source:${PASS}@${ICE_HOST}:${ICE_PORT}${MOUNT}"

log() { echo "$(date -Iseconds) $*" >&2; }


if [[ -s "$list" ]]; then
  log "radio starting from playlist $(wc -l < "$list") files"
  exec ffmpeg -hide_banner -loglevel warning -re \
    -f concat -safe 0 -stream_loop -1 -i "$list" \
    -ac 2 -ar 44100 -c:a libmp3lame -b:a 128k \
    -content_type audio/mpeg \
    -f mp3 "$ICE_URL"
else
  log "radio starting quiet bed (no Current audio yet)"
  # low pink noise bed — always-on carrier for streamer
  exec ffmpeg -hide_banner -loglevel warning -re \
    -f lavfi -i "anoisesrc=color=pink:amplitude=0.02:sample_rate=44100" \
    -ac 2 -ar 44100 -c:a libmp3lame -b:a 96k \
    -content_type audio/mpeg \
    -f mp3 "$ICE_URL"
fi
