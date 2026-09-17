#!/usr/bin/env bash
# Public-domain English Bibles into ./bibles/
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)/bibles"
mkdir -p "$DIR"
BASE=https://raw.githubusercontent.com/scrollmapper/bible_databases/master/formats/csv
# English PD + original-language: Hebrew WLC, Greek Byzantine/TR, Catholic PD English
for v in KJV ASV YLT Darby BBE WLC Byz TR CPDV; do
  curl -fsSL --max-time 180 -o "$DIR/$v.csv" "$BASE/$v.csv"
done
curl -fsSL --max-time 120 -o "$DIR/WEB_source.csv" \
  https://raw.githubusercontent.com/alshival/super_bible/main/SUPER_BIBLE/version_files/super_bible_WEB.csv
python3 - "$DIR" <<'PY'
import csv, re, sys
from pathlib import Path
d = Path(sys.argv[1])
src, dst = d / "WEB_source.csv", d / "WEB.csv"
foot = re.compile(r"\{[^}]*\}")
with src.open(newline="", encoding="utf-8") as inf, dst.open("w", newline="", encoding="utf-8") as out:
    w = csv.writer(out)
    w.writerow(["Book", "Chapter", "Verse", "Text"])
    for row in csv.DictReader(inf):
        title = (row.get("title") or "").strip()
        text = foot.sub("", row.get("text") or "").replace("  ", " ").strip()
        if title and text:
            w.writerow([title, row.get("chapter"), row.get("verse"), text])
src.unlink(missing_ok=True)
print("ok", dst)
PY
