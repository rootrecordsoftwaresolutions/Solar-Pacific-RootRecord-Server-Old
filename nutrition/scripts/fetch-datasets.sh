#!/bin/bash
# Fetch USDA + CORGIS + Open Food Facts onto Media. Resume-safe.
set -euo pipefail
exec python3 "$(dirname "$0")/fetch-datasets.py" "$@"
