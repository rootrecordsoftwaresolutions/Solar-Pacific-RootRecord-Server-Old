#!/usr/bin/env bash
# Re-download public-domain Bible files into this skill store (WEBU + originals).
set -euo pipefail
STORE="$(cd "$(dirname "$0")/../store" && pwd)"
TMP="${TMPDIR:-/tmp}/bible-prayers-dl"
CSV="$STORE/versions"
mkdir -p "$STORE" "$TMP" "$CSV"

if [[ "${1:-}" != "--versions-only" ]]; then
  curl -fsSL -o "$STORE/web.epub" "https://eBible.org/epub/eng-web.epub"
  curl -fsSL -o "$TMP/complete-bible.json" \
    "https://raw.githubusercontent.com/ringletech/webu-open-bible/main/json/complete-bible.json"
  curl -fsSL -o "$STORE/webu-metadata.json" \
    "https://raw.githubusercontent.com/ringletech/webu-open-bible/main/metadata.json"
  gzip -c "$TMP/complete-bible.json" > "$STORE/web.json.gz"
  python3 "$(dirname "$0")/lookup.py" --import "$TMP/complete-bible.json"
fi

BASE=https://raw.githubusercontent.com/scrollmapper/bible_databases/master/formats/csv
# Hebrew OT, Greek NT, then public-domain English (no NIV/ESV/NLT).
for v in WLC Byz TR YLT KJV ASV BBE Darby CPDV; do
  curl -fsSL --max-time 180 -o "$CSV/$v.csv" "$BASE/$v.csv"
done
curl -fsSL --max-time 180 -o "$CSV/WEB_source.csv" \
  https://raw.githubusercontent.com/alshival/super_bible/main/SUPER_BIBLE/version_files/super_bible_WEB.csv
python3 - "$CSV" <<'PY'
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
python3 "$(dirname "$0")/fetch-enoch.py"
python3 "$(dirname "$0")/lookup.py" --import-versions
echo "ok $STORE"
