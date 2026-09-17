#!/usr/bin/env bash
# Ava multi-repo GitHub push wrapper (canonical entry for systemd / hooks).
# See scripts/ava-github-push.mjs for repos, branches, and safety rules.
set -euo pipefail
REPO="/home/rootrecord/.ollama/skills/origin"
cd "$REPO"
exec node "/home/rootrecord/.ollama/skills/git-auto-push/scripts/ava-github-push.mjs" "$@"
