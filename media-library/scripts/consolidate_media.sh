#!/usr/bin/env bash
# Consolidate unique Ava media into the OmniBook home library:
#   /home/rootrecord/Media
# Copy, don't delete sources. Never touch Litecoin blockchain or node files.
# Datadir stays at /mnt/Archives/Litecoin. No move, no rsync, no symlink rewrite.
set -euo pipefail

MEDIA="${AVA_MEDIA_DIR:-$HOME/Media}"
BAK="${MEDIA}.bak-20260915"
ARCHIVES="/mnt/Archives"
PROJECTS="/mnt/Projects"
LOG="${TMPDIR:-/tmp}/ava-media-consolidate.log"
CORE="${AVA_CORE:-$HOME/RootRecord/Ava-Core}"
LTC_DIR="/mnt/Archives/Litecoin"

_is_ltc_path() {
  local p="${1%/}"
  case "$p" in
    "$LTC_DIR"|"$LTC_DIR"/*) return 0 ;;
    "$HOME/Litecoin"|"$HOME/Litecoin"/*) return 0 ;;
    *'/Litecoin'|*'/.litecoin'|*'/litecoin-qt'*) return 0 ;;
    *'/Litecoin/'*|*'/.litecoin/'*) return 0 ;;
  esac
  return 1
}

rsync_gap() {
  local src="$1" dest="$2"
  shift 2
  mkdir -p "$dest"
  if [ ! -e "$src" ]; then
    echo "  skip missing $src"
    return 0
  fi
  if _is_ltc_path "$src" || _is_ltc_path "$dest"; then
    echo "  refuse Litecoin/node path $src"
    return 0
  fi
  echo "  rsync --ignore-existing $src -> $dest"
  rsync -a --ignore-existing --info=stats0,flist0 \
    --exclude 'SteamLibrary/' --exclude 'Litecoin/' --exclude 'litecoin/' \
    --exclude '.litecoin/' --exclude 'blocks/' --exclude 'chainstate/' \
    --exclude 'wallets/' --exclude 'blk*.dat' --exclude 'rev*.dat' \
    --exclude 'peers.dat' --exclude 'mempool.dat' --exclude 'banlist.dat' \
    --exclude 'fee_estimates.dat' --exclude 'ollama-models/' \
    --exclude 'Lala/' --exclude '*.log' --exclude 'node_modules/' \
    "$@" "$src" "$dest"
}

echo "=== Consolidate media ===" | tee "$LOG"
echo "Canonical: $MEDIA" | tee -a "$LOG"
echo "Litecoin datadir stays put: $LTC_DIR (never copy/move)" | tee -a "$LOG"
date | tee -a "$LOG"

mkdir -p \
  "$MEDIA/public/audio/station" "$MEDIA/public/audio/reports" "$MEDIA/public/audio/crons" \
  "$MEDIA/public/audio/words" "$MEDIA/public/audio/numbers" "$MEDIA/public/audio/time_clips" \
  "$MEDIA/public/audio/sounds" "$MEDIA/public/audio/generated" \
  "$MEDIA/public/audio/voice/numbers" "$MEDIA/public/audio/voice/words" \
  "$MEDIA/public/video/clips" "$MEDIA/public/video/reports" "$MEDIA/public/video/current" \
  "$MEDIA/public/images/channels" "$MEDIA/public/images/character" "$MEDIA/public/images/thumbnails" \
  "$MEDIA/public/images/brand" \
  "$MEDIA/private" \
  "$MEDIA/public/audio/imports/projects-core" \
  "$MEDIA/public/audio/imports/drive-d" \
  "$MEDIA/public/audio/imports/drive-e"

# Voice clips from bak + archives
if [ -d "$BAK" ]; then
  echo "== bak $BAK ==" | tee -a "$LOG"
  rsync_gap "$BAK/public/audio/" "$MEDIA/public/audio/"
  rsync_gap "$BAK/public/video/" "$MEDIA/public/video/"
  rsync_gap "$BAK/public/images/" "$MEDIA/public/images/"
fi

if [ -d "$ARCHIVES/Pre August" ]; then
  echo "== Archives Pre August ==" | tee -a "$LOG"
  rsync_gap "$ARCHIVES/Pre August/" "$MEDIA/public/audio/imports/drive-e/" \
    --include '*/' --include '*.mp3' --include '*.wav' --include '*.ogg' \
    --include '*.flac' --include '*.m4a' --exclude '*'
fi
if [ -d "$ARCHIVES/September" ]; then
  echo "== Archives September ==" | tee -a "$LOG"
  rsync_gap "$ARCHIVES/September/" "$MEDIA/public/audio/imports/drive-d/" \
    --include '*/' --include '*.mp3' --include '*.wav' --include '*.ogg' \
    --include '*.flac' --include '*.m4a' --exclude '*'
fi

if [ -d "$PROJECTS/RootRecord Core" ]; then
  echo "== Projects RootRecord Core ==" | tee -a "$LOG"
  rsync_gap "$PROJECTS/RootRecord Core/" "$MEDIA/public/audio/imports/projects-core/" \
    --include '*/' --include '*.mp3' --include '*.wav' --include '*.ogg' \
    --include '*.png' --include '*.jpg' --include '*.webp' --exclude '*'
fi

# CHECKPOINTS / 3.6T disk: do not walk. Could sit next to node data.

# Number clips: keep both public/audio/numbers and voice/numbers filled
if [ -d "$MEDIA/public/audio/voice/numbers" ] && [ -d "$MEDIA/public/audio/numbers" ]; then
  rsync_gap "$MEDIA/public/audio/voice/numbers/" "$MEDIA/public/audio/numbers/"
  rsync_gap "$MEDIA/public/audio/numbers/" "$MEDIA/public/audio/voice/numbers/"
fi

# Pointers — never a second copy of the library
if [ -d "$CORE" ]; then
  ln -sfn "$MEDIA" "$CORE/Media"
  echo "  $CORE/Media -> $MEDIA" | tee -a "$LOG"
fi
if [ -d "$BAK" ] && [ ! -f "$BAK/MOVED.txt" ]; then
  printf 'Moved to %s on %s. This tree is a leftover copy.\n' "$MEDIA" "$(date -Iseconds)" > "$BAK/MOVED.txt"
fi

{
  echo ""
  echo "=== counts ==="
  echo -n "audio files:     "; find "$MEDIA/public/audio" -type f 2>/dev/null | wc -l
  echo -n "numbers:         "; find "$MEDIA/public/audio/numbers" -type f 2>/dev/null | wc -l
  echo -n "voice/numbers:   "; find "$MEDIA/public/audio/voice/numbers" -type f 2>/dev/null | wc -l
  echo -n "time_clips:      "; find "$MEDIA/public/audio/time_clips" -type f 2>/dev/null | wc -l
  du -sh "$MEDIA" 2>/dev/null || true
} | tee -a "$LOG"

echo "Done. Log: $LOG"
