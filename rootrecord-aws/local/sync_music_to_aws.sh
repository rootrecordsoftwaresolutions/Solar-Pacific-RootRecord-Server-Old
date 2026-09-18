#!/usr/bin/env bash
# Sync local music beds → AWS radio/media as MP3 (skip voice word clips).
set -euo pipefail
HOST="${RR_SSH_HOST:-rr-aws}"
SRC="${RR_MUSIC_SRC:-$HOME/Media/public/audio/music}"
REMOTE="/home/ubuntu/rootrecord/radio/media"
STAGE="${TMPDIR:-/tmp}/rr-music-mp3"

if [[ ! -d "$SRC" ]]; then
  echo "missing music src $SRC"
  exit 1
fi

free_kb="$(ssh "$HOST" "df -Pk / | awk 'NR==2{print \$4}'")"
if [[ "${free_kb:-0}" -lt 800000 ]]; then
  echo "AWS free space too low (${free_kb}KB)"
  exit 1
fi

rm -rf "$STAGE"
mkdir -p "$STAGE"
echo "Converting music → mp3 in $STAGE …"
mapfile -d '' -t files < <(find "$SRC" -type f \( -iname '*.wav' -o -iname '*.flac' -o -iname '*.ogg' -o -iname '*.m4a' -o -iname '*.mp3' \) -print0 | sort -z)
echo "found ${#files[@]} source files"
for f in "${files[@]}"; do
  [[ -f "$f" ]] || continue
  rel="${f#"$SRC"/}"
  parent="$(dirname "$rel")"
  base="$(basename "$f")"
  stem="${base%.*}"
  if [[ "$parent" == "." ]]; then
    out="$STAGE/${stem}.mp3"
  else
    safe_parent="$(printf '%s' "$parent" | tr '/ ' '__')"
    mkdir -p "$STAGE/$safe_parent"
    out="$STAGE/$safe_parent/${stem}.mp3"
  fi
  if [[ -f "$out" ]]; then
    continue
  fi
  case "${f,,}" in
    *.mp3) cp -n -- "$f" "$out" ;;
    *)
      ffmpeg -y -hide_banner -loglevel error -i "$f" -codec:a libmp3lame -b:a 128k "$out" \
        || echo "SKIP $f"
      ;;
  esac
done

echo "Uploading to $HOST:$REMOTE"
ssh "$HOST" "mkdir -p '$REMOTE'"
rsync -az --delete "$STAGE"/ "$HOST:$REMOTE/"
count="$(ssh "$HOST" "find '$REMOTE' -type f -name '*.mp3' | wc -l")"
bytes="$(ssh "$HOST" "du -sh '$REMOTE' | awk '{print \$1}'")"
echo "remote mp3 count=$count size=$bytes"
ssh "$HOST" 'sudo systemctl restart rr-radio.service || true'
du -sh "$STAGE"
echo MUSIC_SYNC_OK
