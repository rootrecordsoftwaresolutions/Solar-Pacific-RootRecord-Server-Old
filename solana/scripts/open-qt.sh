#!/usr/bin/env bash
# Solana has no Qt. Zenity is the datadir picker. No mining. No sends. No keygen.
set -euo pipefail
STATE="${AVA_SOLANA_STATE:-$HOME/.ollama/skills/solana/state}"
mkdir -p "$STATE"
export DISPLAY="${DISPLAY:-:0}"
if ! command -v zenity >/dev/null 2>&1; then
  echo "zenity missing" >&2
  exit 1
fi
dir=$(zenity --file-selection --directory --title="Solana — choose ledger / data directory" --filename="/mnt/Archives/" || true)
if [ -z "${dir:-}" ]; then
  echo "cancelled"
  exit 0
fi
printf '%s\n' "$dir" > "$STATE/ledger-dir.txt"
zenity --info --title="Solana" --text="Ledger directory set.

$dir

Mainnet validator is not started from here. CLI is in ~/.ollama/skills/solana/bin."
