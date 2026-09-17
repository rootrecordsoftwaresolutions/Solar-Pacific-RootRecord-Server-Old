#!/usr/bin/env bash
# If Ava-owned trees have real changes, commit and push to Ava-Core-Dev.
# Safe: never stages .env / keys; never force-pushes main/master.
# Quiet when there is nothing to do (suitable for a 15-minute timer).
#
# Covers (via scripts/ava-github-push.mjs):
#   ava-core (+ branch `dev`), ava-core-private (+ `dev`),
#   all-connections (+ `dev`), web-files (+ `dev`),
#   ollama-skills (private, ~/.ollama/skills in place),
#   Vercel sites from each skill's site/ (git in site/.git — one tree only)
# Plugins sync into ava-core-private under workstations/minecraft-plugins/plugins.
set -euo pipefail

REPO="${HOME}/.ollama/skills/origin/repo"
SKILLS="${HOME}/.ollama/skills"
LOCK="${XDG_RUNTIME_DIR:-/tmp}/ava-auto-push.lock"
LOG_DIR="${AVA_AUTO_PUSH_LOG_DIR:-$SKILLS/logs/store}"
LOG="$LOG_DIR/auto-push.log"
mkdir -p "$LOG_DIR"

log() { printf '%s %s\n' "$(date -Iseconds)" "$*" | tee -a "$LOG"; }

exec 9>"$LOCK"
if ! flock -n 9; then
  exit 0
fi

FLAG="${XDG_STATE_HOME:-$HOME/.local/state}/ava/github-auto-push.off"
if [ -f "$FLAG" ]; then
  # Quiet exit when operator (or /ops) disabled auto-push for Emergent / manual work.
  # Still leave a breadcrumb so "timer succeeded" is not mistaken for a push.
  mkdir -p "$LOG_DIR"
  printf '%s auto-push disabled by flag %s\n' "$(date -Iseconds)" "$FLAG" >>"$LOG"
  exit 0
fi

if [ ! -f "$SKILLS/state/store/ava-console-up" ]; then
  exit 0
fi
cd "$SKILLS"

if command -v node >/dev/null 2>&1; then
  if node "/home/rootrecord/.ollama/skills/git-auto-push/scripts/ava-github-push.mjs" >>"$LOG" 2>&1; then
    log "canonical multi-repo push ok"
  else
    log "canonical multi-repo push skipped/failed — see $LOG"
    exit 1
  fi
else
  log "skip: node not found"
fi
