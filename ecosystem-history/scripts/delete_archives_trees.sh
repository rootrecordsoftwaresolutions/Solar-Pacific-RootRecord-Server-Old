#!/usr/bin/env bash
# Delete only the named Archives trees after the docs pull. Never Litecoin/Steam/ollama-models.
# NTFS leftover husks with ':' in the name need streams_interface=xattr remount (root).
set -euo pipefail
ROOT=/mnt/Archives
ALLOW=(
  "$ROOT/.git"
  "$ROOT/.Trash-1000"
  "$ROOT/August 2026"
  "$ROOT/September"
  "$ROOT/db backup"
  "$ROOT/Pre August"
)
for p in "${ALLOW[@]}"; do
  case "$p" in
    *Litecoin*|*SteamLibrary*|*ollama-models*)
      echo "refuse $p"
      exit 1
      ;;
  esac
  if [ ! -e "$p" ]; then
    echo "already gone $p"
    continue
  fi
  echo "rm -rf $p"
  rm -rf --one-file-system "$p"
  if [ -e "$p" ]; then
    echo "FAILED still exists $p" >&2
    exit 1
  fi
  echo "gone $p"
done
echo "=== remaining Archives ==="
ls -la "$ROOT"
