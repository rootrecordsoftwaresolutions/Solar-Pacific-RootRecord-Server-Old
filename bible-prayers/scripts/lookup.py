#!/usr/bin/env python3
"""Look up local public-domain Bible verses (Hebrew, Greek, English).

Public domain. Do not invent verses. Quote what this file returns.
"""
from __future__ import annotations

import argparse
import csv
import gzip
import json
import re
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SKILL = HERE.parent
STORE = SKILL / "store"
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))
DB = STORE / "web.sqlite"
BIBLES_DB = STORE / "bibles.sqlite"
JSON_GZ = STORE / "web.json.gz"
VERSION_DIR = STORE / "versions"
CSV_VERSIONS = ("WLC", "Byz", "TR", "YLT", "KJV", "WEB", "ASV", "BBE", "Darby", "CPDV", "Enoch")
DEFAULT_VERSIONS = ("WLC", "Byz", "YLT", "WEBU", "Enoch")

CANON = (
    "Genesis",
    "Exodus",
    "Leviticus",
    "Numbers",
    "Deuteronomy",
    "Joshua",
    "Judges",
    "Ruth",
    "1 Samuel",
    "2 Samuel",
    "1 Kings",
    "2 Kings",
    "1 Chronicles",
    "2 Chronicles",
    "Ezra",
    "Nehemiah",
    "Esther",
    "Job",
    "Psalms",
    "Proverbs",
    "Ecclesiastes",
    "Song of Solomon",
    "Isaiah",
    "Jeremiah",
    "Lamentations",
    "Ezekiel",
    "Daniel",
    "Hosea",
    "Joel",
    "Amos",
    "Obadiah",
    "Jonah",
    "Micah",
    "Nahum",
    "Habakkuk",
    "Zephaniah",
    "Haggai",
    "Zechariah",
    "Malachi",
    "Matthew",
    "Mark",
    "Luke",
    "John",
    "Acts",
    "Romans",
    "1 Corinthians",
    "2 Corinthians",
    "Galatians",
    "Ephesians",
    "Philippians",
    "Colossians",
    "1 Thessalonians",
    "2 Thessalonians",
    "1 Timothy",
    "2 Timothy",
    "Titus",
    "Philemon",
    "Hebrews",
    "James",
    "1 Peter",
    "2 Peter",
    "1 John",
    "2 John",
    "3 John",
    "Jude",
    "Revelation",
    "Enoch",
    "Tobit",
    "Judith",
    "Wisdom",
    "Sirach",
    "Baruch",
    "1 Maccabees",
    "2 Maccabees",
    "Prayer of Manasses",
    "1 Esdras",
    "2 Esdras",
    "Laodiceans",
)

ALIASES: dict[str, str] = {}
for _name in CANON:
    ALIASES[_name.lower()] = _name
    ALIASES[_name.lower().replace(" ", "")] = _name
    ALIASES[_name.lower().replace(" ", "-")] = _name

_EXTRA = {
    "gen": "Genesis",
    "ge": "Genesis",
    "ex": "Exodus",
    "exo": "Exodus",
    "exod": "Exodus",
    "lev": "Leviticus",
    "le": "Leviticus",
    "num": "Numbers",
    "nu": "Numbers",
    "deut": "Deuteronomy",
    "dt": "Deuteronomy",
    "de": "Deuteronomy",
    "josh": "Joshua",
    "jos": "Joshua",
    "jdg": "Judges",
    "judg": "Judges",
    "jdgs": "Judges",
    "ru": "Ruth",
    "rth": "Ruth",
    "1sam": "1 Samuel",
    "1sa": "1 Samuel",
    "i samuel": "1 Samuel",
    "first samuel": "1 Samuel",
    "2sam": "2 Samuel",
    "2sa": "2 Samuel",
    "ii samuel": "2 Samuel",
    "second samuel": "2 Samuel",
    "1kgs": "1 Kings",
    "1ki": "1 Kings",
    "i kings": "1 Kings",
    "2kgs": "2 Kings",
    "2ki": "2 Kings",
    "ii kings": "2 Kings",
    "1chr": "1 Chronicles",
    "1ch": "1 Chronicles",
    "i chronicles": "1 Chronicles",
    "2chr": "2 Chronicles",
    "2ch": "2 Chronicles",
    "ii chronicles": "2 Chronicles",
    "ezr": "Ezra",
    "neh": "Nehemiah",
    "est": "Esther",
    "es": "Esther",
    "ps": "Psalms",
    "psa": "Psalms",
    "psalm": "Psalms",
    "pss": "Psalms",
    "prov": "Proverbs",
    "pr": "Proverbs",
    "prv": "Proverbs",
    "eccl": "Ecclesiastes",
    "ecc": "Ecclesiastes",
    "qoheleth": "Ecclesiastes",
    "song": "Song of Solomon",
    "sos": "Song of Solomon",
    "so": "Song of Solomon",
    "ss": "Song of Solomon",
    "canticle": "Song of Solomon",
    "canticles": "Song of Solomon",
    "song of songs": "Song of Solomon",
    "isa": "Isaiah",
    "is": "Isaiah",
    "jer": "Jeremiah",
    "je": "Jeremiah",
    "lam": "Lamentations",
    "ezek": "Ezekiel",
    "eze": "Ezekiel",
    "dan": "Daniel",
    "da": "Daniel",
    "hos": "Hosea",
    "joe": "Joel",
    "am": "Amos",
    "obad": "Obadiah",
    "ob": "Obadiah",
    "jnh": "Jonah",
    "jon": "Jonah",
    "mic": "Micah",
    "nah": "Nahum",
    "hab": "Habakkuk",
    "zeph": "Zephaniah",
    "zep": "Zephaniah",
    "hag": "Haggai",
    "zech": "Zechariah",
    "zec": "Zechariah",
    "mal": "Malachi",
    "mt": "Matthew",
    "matt": "Matthew",
    "mat": "Matthew",
    "mk": "Mark",
    "mrk": "Mark",
    "mar": "Mark",
    "lk": "Luke",
    "luk": "Luke",
    "jn": "John",
    "jhn": "John",
    "joh": "John",
    "ac": "Acts",
    "act": "Acts",
    "rom": "Romans",
    "ro": "Romans",
    "1cor": "1 Corinthians",
    "1co": "1 Corinthians",
    "i corinthians": "1 Corinthians",
    "2cor": "2 Corinthians",
    "2co": "2 Corinthians",
    "ii corinthians": "2 Corinthians",
    "gal": "Galatians",
    "ga": "Galatians",
    "eph": "Ephesians",
    "phil": "Philippians",
    "php": "Philippians",
    "phpil": "Philippians",
    "col": "Colossians",
    "1thess": "1 Thessalonians",
    "1th": "1 Thessalonians",
    "2thess": "2 Thessalonians",
    "2th": "2 Thessalonians",
    "1tim": "1 Timothy",
    "1ti": "1 Timothy",
    "2tim": "2 Timothy",
    "2ti": "2 Timothy",
    "tit": "Titus",
    "phm": "Philemon",
    "phlm": "Philemon",
    "heb": "Hebrews",
    "jas": "James",
    "jam": "James",
    "1pet": "1 Peter",
    "1pe": "1 Peter",
    "2pet": "2 Peter",
    "2pe": "2 Peter",
    "1jn": "1 John",
    "1jhn": "1 John",
    "1john": "1 John",
    "i john": "1 John",
    "2jn": "2 John",
    "2john": "2 John",
    "ii john": "2 John",
    "3jn": "3 John",
    "3john": "3 John",
    "iii john": "3 John",
    "jud": "Jude",
    "rev": "Revelation",
    "re": "Revelation",
    "apocalypse": "Revelation",
    "revelation of john": "Revelation",
    "i corinthians": "1 Corinthians",
    "ii corinthians": "2 Corinthians",
    "i thessalonians": "1 Thessalonians",
    "ii thessalonians": "2 Thessalonians",
    "i timothy": "1 Timothy",
    "ii timothy": "2 Timothy",
    "i peter": "1 Peter",
    "ii peter": "2 Peter",
    "iii john": "3 John",
    "enoch": "Enoch",
    "1 enoch": "Enoch",
    "i enoch": "Enoch",
    "book of enoch": "Enoch",
    "ethiopic enoch": "Enoch",
    "ethiopian enoch": "Enoch",
    "tobit": "Tobit",
    "judith": "Judith",
    "wisdom": "Wisdom",
    "wisdom of solomon": "Wisdom",
    "sirach": "Sirach",
    "ecclesiasticus": "Sirach",
    "baruch": "Baruch",
    "1 maccabees": "1 Maccabees",
    "2 maccabees": "2 Maccabees",
    "i maccabees": "1 Maccabees",
    "ii maccabees": "2 Maccabees",
    "1 mac": "1 Maccabees",
    "2 mac": "2 Maccabees",
    "prayer of manasses": "Prayer of Manasses",
    "prayer of manasseh": "Prayer of Manasses",
    "1 esdras": "1 Esdras",
    "2 esdras": "2 Esdras",
    "i esdras": "1 Esdras",
    "ii esdras": "2 Esdras",
    "laodiceans": "Laodiceans",
}
ALIASES.update(_EXTRA)

DEUTERO = frozenset(
    {
        "Tobit",
        "Judith",
        "Wisdom",
        "Sirach",
        "Baruch",
        "1 Maccabees",
        "2 Maccabees",
        "Prayer of Manasses",
        "1 Esdras",
        "2 Esdras",
        "Laodiceans",
        "Enoch",
    }
)


def _merge_learned_aliases() -> None:
    try:
        from learn import aliases as learned_aliases
    except ImportError:
        return
    for k, v in learned_aliases().items():
        ALIASES.setdefault(k.lower(), v)


_merge_learned_aliases()

REF_RE = re.compile(
    r"""
    ^\s*
    (?P<book>.+?)
    \s+
    (?P<chapter>\d+)
    (?:
        \s* : \s*
        (?P<start>\d+)
        (?:
            \s* [-–] \s*
            (?P<end>\d+)
        )?
    )?
    \s*$
    """,
    re.I | re.X,
)


def import_json(src: Path, dest: Path = DB) -> int:
    if src.suffix == ".gz":
        raw = gzip.decompress(src.read_bytes())
        verses = json.loads(raw.decode("utf-8"))
    else:
        verses = json.loads(src.read_text(encoding="utf-8"))
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        dest.unlink()
    con = sqlite3.connect(dest)
    try:
        con.execute(
            """
            CREATE TABLE verses (
                book TEXT NOT NULL,
                chapter INTEGER NOT NULL,
                verse INTEGER NOT NULL,
                text TEXT NOT NULL,
                PRIMARY KEY (book, chapter, verse)
            )
            """
        )
        con.executemany(
            "INSERT INTO verses (book, chapter, verse, text) VALUES (?, ?, ?, ?)",
            (
                (row["book"], int(row["chapter"]), int(row["verse"]), row["text"])
                for row in verses
            ),
        )
        con.execute("CREATE INDEX verses_book_ch ON verses(book, chapter)")
        con.commit()
        n = con.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
    finally:
        con.close()
    return int(n)


def import_versions(dest: Path = BIBLES_DB) -> dict[str, int]:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        dest.unlink()
    con = sqlite3.connect(dest)
    counts: dict[str, int] = {}
    try:
        con.execute(
            """
            CREATE TABLE verses (
                version TEXT NOT NULL,
                book TEXT NOT NULL,
                chapter INTEGER NOT NULL,
                verse INTEGER NOT NULL,
                text TEXT NOT NULL,
                PRIMARY KEY (version, book, chapter, verse)
            )
            """
        )
        for abbr in CSV_VERSIONS:
            path = VERSION_DIR / f"{abbr}.csv"
            if not path.is_file():
                counts[abbr] = 0
                continue
            rows: list[tuple[str, str, int, int, str]] = []
            with path.open(newline="", encoding="utf-8") as fh:
                for raw in csv.DictReader(fh):
                    book = resolve_book(raw.get("Book") or "")
                    text = (raw.get("Text") or "").strip()
                    if not book or not text:
                        continue
                    try:
                        ch = int(raw.get("Chapter") or 0)
                        vs = int(raw.get("Verse") or 0)
                    except ValueError:
                        continue
                    if ch and vs:
                        rows.append((abbr, book, ch, vs, text))
            con.executemany(
                "INSERT OR REPLACE INTO verses (version, book, chapter, verse, text) "
                "VALUES (?, ?, ?, ?, ?)",
                rows,
            )
            counts[abbr] = len(rows)
        if DB.is_file():
            src = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
            try:
                web_rows = [
                    ("WEBU", r[0], int(r[1]), int(r[2]), r[3])
                    for r in src.execute(
                        "SELECT book, chapter, verse, text FROM verses"
                    )
                    if r[3]
                ]
            finally:
                src.close()
            con.executemany(
                "INSERT OR REPLACE INTO verses (version, book, chapter, verse, text) "
                "VALUES (?, ?, ?, ?, ?)",
                web_rows,
            )
            counts["WEBU"] = len(web_rows)
        con.execute(
            "CREATE INDEX verses_lookup ON verses(book, chapter, verse, version)"
        )
        con.commit()
    finally:
        con.close()
    return counts


def connect() -> sqlite3.Connection:
    if not DB.is_file():
        raise SystemExit(f"missing {DB}; run: python3 {HERE / 'lookup.py'} --import")
    con = sqlite3.connect(f"file:{DB}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    return con


def resolve_book(raw: str) -> str | None:
    key = re.sub(r"\s+", " ", raw.strip().lower())
    key = key.replace(".", "")
    return ALIASES.get(key) or ALIASES.get(key.replace(" ", ""))


def parse_ref(text: str) -> tuple[str, int, int, int] | None:
    m = REF_RE.match(text.strip())
    if not m:
        return None
    book = resolve_book(m.group("book"))
    if not book:
        return None
    chapter = int(m.group("chapter"))
    start = int(m.group("start") or 1)
    end = int(m.group("end") or (m.group("start") or 0) or 0)
    if not m.group("start"):
        start, end = 0, 0
    elif not m.group("end"):
        end = start
    if end and end < start:
        start, end = end, start
    return book, chapter, start, end


def fetch_ref(ref: str, versions: tuple[str, ...] | None = None) -> list[sqlite3.Row]:
    parsed = parse_ref(ref)
    if not parsed:
        return []
    book, chapter, start, end = parsed
    if versions:
        wanted = versions
    elif book in DEUTERO:
        wanted = ("Enoch", "CPDV", "YLT", "WEBU")
    else:
        wanted = DEFAULT_VERSIONS
    if BIBLES_DB.is_file():
        con = sqlite3.connect(f"file:{BIBLES_DB}?mode=ro", uri=True)
        con.row_factory = sqlite3.Row
        try:
            marks = ",".join("?" * len(wanted))
            if start == 0:
                sql = (
                    "SELECT version, book, chapter, verse, text FROM verses "
                    f"WHERE book = ? AND chapter = ? AND version IN ({marks}) "
                    "ORDER BY verse, version"
                )
                params: tuple = (book, chapter, *wanted)
            else:
                sql = (
                    "SELECT version, book, chapter, verse, text FROM verses "
                    f"WHERE book = ? AND chapter = ? AND verse BETWEEN ? AND ? "
                    f"AND version IN ({marks}) ORDER BY verse, version"
                )
                params = (book, chapter, start, end, *wanted)
            rows = list(con.execute(sql, params))
            order = {name: i for i, name in enumerate(wanted)}
            rows.sort(key=lambda r: (int(r["verse"]), order.get(r["version"], 99)))
            return rows
        finally:
            con.close()
    con = connect()
    try:
        if start == 0:
            return list(
                con.execute(
                    "SELECT book, chapter, verse, text FROM verses "
                    "WHERE book = ? AND chapter = ? ORDER BY verse",
                    (book, chapter),
                )
            )
        return list(
            con.execute(
                "SELECT book, chapter, verse, text FROM verses "
                "WHERE book = ? AND chapter = ? AND verse BETWEEN ? AND ? "
                "ORDER BY verse",
                (book, chapter, start, end),
            )
        )
    finally:
        con.close()


def search_text(q: str, limit: int = 20) -> list[sqlite3.Row]:
    con = connect()
    try:
        return list(
            con.execute(
                "SELECT book, chapter, verse, text FROM verses "
                "WHERE text LIKE ? ORDER BY book, chapter, verse LIMIT ?",
                (f"%{q}%", limit),
            )
        )
    finally:
        con.close()


def format_rows(rows: list[sqlite3.Row]) -> str:
    if not rows:
        return ""
    keys = rows[0].keys()
    has_version = "version" in keys
    first, last = rows[0], rows[-1]
    if has_version:
        verses = sorted({int(r["verse"]) for r in rows})
        if len(verses) == 1:
            head = f"{first['book']} {first['chapter']}:{verses[0]}"
        else:
            head = f"{first['book']} {first['chapter']}:{verses[0]}-{verses[-1]}"
        lines = [head]
        for row in rows:
            lines.append(f"{row['verse']} ({row['version']}) {row['text']}")
        return "\n".join(lines)
    lines = [f"{first['book']} {first['chapter']} (WEBU)"]
    if len(rows) == 1:
        lines = [f"{first['book']} {first['chapter']}:{first['verse']} (WEBU)"]
    elif first["verse"] != last["verse"]:
        lines = [
            f"{first['book']} {first['chapter']}:{first['verse']}"
            f"-{last['verse']} (WEBU)"
        ]
    for row in rows:
        lines.append(f"{row['verse']} {row['text']}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Look up local public-domain verses.")
    p.add_argument("ref", nargs="?", help='Reference, e.g. "John 3:16" or "Psalm 23:1-6"')
    p.add_argument("--search", help="Plain-text search (local LIKE, WEBU)")
    p.add_argument("--import", dest="import_src", nargs="?", const="-", help="Build sqlite from JSON")
    p.add_argument(
        "--import-versions",
        action="store_true",
        help="Build bibles.sqlite from store/versions CSVs + WEBU",
    )
    p.add_argument(
        "--version",
        action="append",
        dest="versions",
        help="Version label (repeatable): WLC Byz TR YLT KJV WEB ASV BBE Darby CPDV WEBU",
    )
    p.add_argument("--learn-summary", action="store_true", help="Silent learn totals (no verse text)")
    p.add_argument("--limit", type=int, default=20)
    args = p.parse_args(argv)

    if args.learn_summary:
        from learn import summary

        print(summary())
        return 0

    if args.import_versions:
        counts = import_versions()
        bits = " ".join(f"{k}={v}" for k, v in counts.items())
        print(f"imported {bits} -> {BIBLES_DB}")
        return 0

    if args.import_src is not None:
        src = Path(args.import_src) if args.import_src != "-" else None
        if src is None:
            for cand in (
                JSON_GZ,
                STORE / "web.json",
                Path("/tmp/bible-prayers-dl/complete-bible.json"),
            ):
                if cand.is_file():
                    src = cand
                    break
        if src is None or not src.is_file():
            print("no JSON to import", file=sys.stderr)
            return 1
        n = import_json(src)
        print(f"imported {n} verses -> {DB}")
        return 0

    if args.search:
        rows = search_text(args.search, limit=args.limit)
        if not rows:
            print("no hits")
            return 1
        print(format_rows(rows) if len(rows) == 1 else "\n\n".join(
            f"{r['book']} {r['chapter']}:{r['verse']} (WEBU)\n{r['verse']} {r['text']}"
            for r in rows
        ))
        return 0

    if not args.ref:
        p.print_help()
        return 2
    versions = tuple(v.strip().upper() for v in (args.versions or []) if v.strip()) or None
    rows = fetch_ref(args.ref, versions=versions)
    try:
        from learn import apply, note

        parsed = parse_ref(args.ref)
        note(
            kind="hit" if rows else "miss",
            ref=args.ref,
            book=(parsed[0] if parsed else args.ref.split()[0]),
            ok=bool(rows),
            surface="lookup",
        )
        if not rows:
            apply()
    except Exception:
        pass
    if not rows:
        print(f"not found: {args.ref}", file=sys.stderr)
        return 1
    print(format_rows(rows))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
