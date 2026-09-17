#!/usr/bin/env bash
set -euo pipefail

APP="/home/rootrecord/RootRecord/Ava-Core/sites/alexrs94-site"
PROJECT="alexrs94-site"

bash "/home/rootrecord/.ollama/skills/public-edge/scripts/deploy-next-to-pages.sh" "$APP" "$PROJECT"
