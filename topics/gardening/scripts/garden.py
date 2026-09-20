#!/usr/bin/env python3
"""USDA-zone gardening desk. Polyculture default. No invented yields or ZIP zones."""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(
    os.environ.get(
        "GARDENING_ROOT",
        str(Path.home() / ".ollama" / "skills" / "gardening"),
    )
)
ZONES_PATH = ROOT / "store" / "zones.json"
GUILDS_PATH = ROOT / "store" / "guilds.json"
CLIMATES_PATH = ROOT / "store" / "climates.json"
ZONES_DIR = ROOT / "zones"


def _load(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _zone_id(raw: str) -> str:
    text = (raw or "").strip().lower()
    text = text.replace("zone", "").replace("usda", "").strip()
    m = re.match(r"^(\d{1,2})", text)
    if not m:
        return ""
    return f"{int(m.group(1)):02d}"


def zones() -> list[dict[str, Any]]:
    rows = _load(ZONES_PATH).get("zones") or []
    return [z for z in rows if isinstance(z, dict) and z.get("id")]


def get_zone(raw: str) -> dict[str, Any] | None:
    want = _zone_id(raw)
    if not want:
        return None
    for z in zones():
        if z.get("id") == want:
            return z
    return None


def guilds(*, zone: str = "", hawaii_only: bool = False) -> list[dict[str, Any]]:
    rows = _load(GUILDS_PATH).get("guilds") or []
    out: list[dict[str, Any]] = []
    zid = _zone_id(zone) if zone else ""
    for g in rows:
        if not isinstance(g, dict) or not g.get("id"):
            continue
        if hawaii_only and not g.get("hawaii"):
            continue
        if zid:
            allowed = [str(x).zfill(2) for x in (g.get("zones") or [])]
            if zid not in allowed:
                continue
        out.append(g)
    return out


def lookup(query: str) -> dict[str, Any]:
    q = (query or "").strip().lower()
    hits: list[dict[str, Any]] = []
    for g in guilds():
        blob = " ".join(
            [
                str(g.get("id") or ""),
                str(g.get("name") or ""),
                str(g.get("source") or ""),
                str(g.get("notes") or ""),
                " ".join(str(x) for x in (g.get("function") or [])),
            ]
        ).lower()
        if q and q not in blob:
            continue
        hits.append(
            {
                "id": g.get("id"),
                "name": g.get("name"),
                "hawaii": bool(g.get("hawaii")),
                "zones": g.get("zones"),
                "source": g.get("source"),
            }
        )
    return {"query": query, "guilds": hits, "invented": False}


def sources() -> dict[str, Any]:
    meta = _load(ZONES_PATH)
    return {
        "phzm": meta.get("url"),
        "gis": meta.get("gis"),
        "plants": "https://plants.usda.gov/",
        "grin": "https://npgsweb.ars-grin.gov/gringlobal",
        "wss": "https://websoilsurvey.nrcs.usda.gov/",
        "nass": "https://quickstats.nass.usda.gov/",
        "nac": "https://www.fs.usda.gov/nac/",
        "efotg": "https://efotg.sc.egov.usda.gov/",
        "ctahr": "https://www.ctahr.hawaii.edu/site/PubList.aspx",
        "ctahr_cover": "https://cms.ctahr.hawaii.edu/soap/Resources/Sustainable-and-Organic-Topics",
        "hdoa": "https://hdoa.hawaii.gov/",
        "hilo": "https://www.ars.usda.gov/pacific-west-area/hilo-hi/daniel-k-inouye-us-pacific-basin-agricultural-research-center/tropical-plant-genetic-resources-and-disease-research/",
        "pmc": "https://www.nrcs.usda.gov/plant-materials/hipmc",
        "hawaii_gis_soils": "https://geodata.hawaii.gov/",
        "rainfall_atlas": "http://rainfall.geography.hawaii.edu/",
        "note": meta.get("note"),
    }


def hawaii() -> dict[str, Any]:
    climates = _load(CLIMATES_PATH)
    return {
        "overlay": str(ZONES_DIR / "hawaii" / "OVERLAY.md"),
        "reference": str(ROOT / "references" / "hawaii.md"),
        "big_island": str(ROOT / "references" / "big-island.md"),
        "lookup_zone": climates.get("phzm") or "https://planthardiness.ars.usda.gov/",
        "rainfall_atlas": climates.get("rainfall_atlas"),
        "point": climates.get("point"),
        "slogans": climates.get("slogans"),
        "zones_named_on_hi_pr_maps": ["12", "13"],
        "guilds": [g.get("id") for g in guilds(hawaii_only=True)],
        "do_not": [
            "collapse Hawaiʻi Island to one USDA zone",
            "guess a ZIP zone or a rainfall inch count",
            "treat PHZM as rainfall",
            "recite all-but-N of Earth's climates",
            "pin Köppen codes on towns without a map on disk",
            "default to monoculture coffee or pineapple",
            "invent noxious-weed clearance",
        ],
    }


def big_island() -> dict[str, Any]:
    data = _load(CLIMATES_PATH)
    data = dict(data)
    data["file"] = str(ROOT / "references" / "big-island.md")
    return data


def write_zone_folders() -> dict[str, Any]:
    meta = _load(ZONES_PATH)
    written = []
    for z in zones():
        zid = str(z.get("id"))
        folder = ZONES_DIR / zid
        folder.mkdir(parents=True, exist_ok=True)
        half = z.get("half") or {}
        hi = z.get("hawaii")
        hi_line = {
            True: "On the 2023 Hawaiʻi / Puerto Rico PHZM maps.",
            False: "Not a Hawaiʻi coastal default. Hawaiʻi high slopes still need a ZIP lookup.",
            "possible-elevation": "May appear on Hawaiʻi with elevation. Look up the ZIP. Do not guess.",
            "possible-high-elevation": "Only possible on Hawaiʻi high ground after ZIP lookup.",
        }.get(hi, str(hi))
        body = "\n".join(
            [
                f"# USDA {z.get('name')} (2023 PHZM)",
                "",
                f"Extreme min: **{half.get('a')}** (a) / **{half.get('b')}** (b).",
                "",
                f"Map: {meta.get('url')}",
                "",
                "## Hawaiʻi",
                "",
                hi_line,
                "",
                "## Polyculture",
                "",
                str(z.get("polyculture") or ""),
                "",
                "Do not design a monoculture stand unless they asked.",
                "Do not invent a planting calendar from the zone number alone.",
                "",
                f"Guilds: `python3 {ROOT / 'scripts' / 'garden.py'} guilds {zid}`",
                "",
            ]
        )
        (folder / "README.md").write_text(body, encoding="utf-8")
        written.append(zid)
    return {"ok": True, "zones": written}


def _dump(obj: Any) -> int:
    print(json.dumps(obj, indent=2, ensure_ascii=False))
    return 0


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if not args or args[0] in {"help", "-h", "--help"}:
        print(
            "garden.py sources|zones|zone N|hawaii|big-island|guilds [N]|lookup Q|seed-zones",
            file=sys.stderr,
        )
        return 0
    cmd = args[0]
    if cmd == "sources":
        return _dump(sources())
    if cmd == "zones":
        return _dump(
            {
                "map": _load(ZONES_PATH).get("map"),
                "url": _load(ZONES_PATH).get("url"),
                "zones": [
                    {"id": z.get("id"), "half": z.get("half"), "hawaii": z.get("hawaii")}
                    for z in zones()
                ],
            }
        )
    if cmd == "zone":
        z = get_zone(args[1] if len(args) > 1 else "")
        if not z:
            return _dump({"ok": False, "error": "unknown_zone"})
        zid = str(z.get("id"))
        return _dump({**z, "folder": str(ZONES_DIR / zid), "guilds": guilds(zone=zid)})
    if cmd in {"hawaii", "big-island", "climates"}:
        if cmd == "hawaii":
            return _dump(hawaii())
        return _dump(big_island())
    if cmd == "guilds":
        zone = args[1] if len(args) > 1 else ""
        return _dump({"lens": "polyculture", "guilds": guilds(zone=zone)})
    if cmd == "lookup":
        q = " ".join(args[1:]).strip()
        if not q:
            return _dump({"ok": False, "error": "empty_query"})
        return _dump(lookup(q))
    if cmd == "seed-zones":
        return _dump(write_zone_folders())
    return _dump({"ok": False, "error": "usage"})


if __name__ == "__main__":
    raise SystemExit(main())
