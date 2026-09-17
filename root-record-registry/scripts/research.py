#!/usr/bin/env python3
"""Root Record Registry — OA catalog. UH ScholarSpace first. No paywall scrape."""
from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

UA = "RootRecord-research-oa/1.0 (local OmniBook OA ingest; https://rootrecord.cloud)"
SKILL = Path(
    os.environ.get("REGISTRY_ROOT")
    or os.environ.get("RESEARCH_OA_ROOT")
    or str(Path(__file__).resolve().parent.parent)
)
MEDIA = Path(os.environ.get("AVA_MEDIA_DIR") or "/home/rootrecord/Media")
DUMP = MEDIA / "public" / "documents" / "research-oa"
CATALOG = SKILL / "store" / "catalog.json"
LINK = SKILL / "store" / "datasets"
CTAHR = "622fd1d3-d29f-4dea-ac7a-609fcf5959a0"
PHIL = "0facb8e5-2f34-4c35-beed-e79f98e896f9"
RELIGION = "4e4b9ebf-70fa-429b-ae33-fef4acd0df39"
MAX_RECORDS = 12000
ABS_CAP = 900
SLEEP = 0.3
PDF_CAP = 40 * 1024 * 1024

ATOM = "{http://www.w3.org/2005/Atom}"
ARXIV_NS = "{http://arxiv.org/schemas/atom}"


def _now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime()) + "-10:00"


def _get(url: str, *, xml: bool = False) -> Any:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=45) as resp:
        raw = resp.read()
    if xml:
        return ET.fromstring(raw)
    return json.loads(raw.decode("utf-8"))


def ss_pdf_url(uuid: str) -> str:
    url = f"https://scholarspace.manoa.hawaii.edu/server/api/core/items/{uuid}/bundles"
    data = _get(url)
    for bundle in (data.get("_embedded") or {}).get("bundles") or []:
        if str(bundle.get("name") or "") != "ORIGINAL":
            continue
        href = ((bundle.get("_links") or {}).get("bitstreams") or {}).get("href")
        if not href:
            continue
        bits = _get(href)
        for bs in (bits.get("_embedded") or {}).get("bitstreams") or []:
            name = str(bs.get("name") or "").lower()
            href_c = ((bs.get("_links") or {}).get("content") or {}).get("href") or ""
            if href_c and (name.endswith(".pdf") or "pdf" in name):
                return href_c
            if href_c and not name.endswith((".jpg", ".png", ".gif")):
                return href_c
    return ""


def _first_md(meta: dict[str, Any], *keys: str) -> str:
    for key in keys:
        rows = meta.get(key) or []
        if rows and isinstance(rows, list) and isinstance(rows[0], dict):
            val = str(rows[0].get("value") or "").strip()
            if val:
                return val
    return ""


def _authors_md(meta: dict[str, Any]) -> str:
    rows = meta.get("dc.contributor.author") or []
    names = [str(r.get("value") or "").strip() for r in rows if isinstance(r, dict)]
    return "; ".join(n for n in names if n)[:240]


def _load() -> dict[str, Any]:
    if not CATALOG.is_file():
        return {"updated": _now(), "source": "desk seed", "records": []}
    try:
        data = json.loads(CATALOG.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"updated": _now(), "source": "desk seed", "records": []}
    return data if isinstance(data, dict) else {"updated": _now(), "records": []}


def _save(data: dict[str, Any]) -> None:
    CATALOG.parent.mkdir(parents=True, exist_ok=True)
    DUMP.mkdir(parents=True, exist_ok=True)
    data = dict(data)
    data["updated"] = _now()
    rows = [r for r in (data.get("records") or []) if isinstance(r, dict) and r.get("id")]
    if len(rows) > MAX_RECORDS:
        rows = rows[-MAX_RECORDS:]
    data["records"] = rows
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    tmp = CATALOG.with_suffix(".tmp")
    tmp.write_text(text, encoding="utf-8")
    tmp.replace(CATALOG)
    (DUMP / "catalog.json").write_text(text, encoding="utf-8")
    if not LINK.exists() and not LINK.is_symlink():
        try:
            LINK.symlink_to(DUMP)
        except OSError:
            pass


def upsert(rows: list[dict[str, Any]]) -> int:
    data = _load()
    by_id = {
        str(r.get("id")): r
        for r in (data.get("records") or [])
        if isinstance(r, dict) and r.get("id")
    }
    added = 0
    for row in rows:
        rid = str(row.get("id") or "")
        if not rid:
            continue
        if rid not in by_id:
            added += 1
        merged = {**by_id.get(rid, {}), **row}
        merged["topics"] = tag_record(merged)
        by_id[rid] = merged
    data["records"] = list(by_id.values())
    _save(data)
    return added


def records() -> list[dict[str, Any]]:
    return [r for r in (_load().get("records") or []) if isinstance(r, dict) and r.get("id")]


TOPIC_KEYS: dict[str, tuple[str, ...]] = {
    "gardening": (
        "kalo",
        "taro",
        "colocasia",
        "ulu",
        "breadfruit",
        "artocarpus",
        "agroforest",
        "cover crop",
        "polyculture",
        "ctahr",
        "crop",
        "farm",
        "soil",
        "loʻi",
        "loi",
        "plant",
        "multi-story",
        "coffee",
        "macadamia",
    ),
    "nutrition": (
        "nutrient",
        "diet",
        "vitamin",
        "food composition",
        "obesity",
        "human resilience",
        "nutrition",
        "health",
    ),
    "history": (
        "kingdom",
        "overthrow",
        "liliʻuokalani",
        "kamehameha",
        "annexation",
        "oral history",
        "hawaiian history",
        "mālama",
        "malama",
        "kipuka",
        "kīpuka",
        "halo",
    ),
    "geography": (
        "rainfall",
        "orographic",
        "giambelluca",
        "climate",
        "elevation",
        "windward",
        "leeward",
    ),
    "fern-forest": ("fern forest", "leila road", "puna lot"),
    "cooking": ("recipe", "cookbook", "cookery"),
    "bible-prayers": ("bible", "prayer", "psalm", "scripture"),
    "weather-kilauea": ("kilauea", "volcano", "earthquake", "seismic"),
    "philosophy": (
        "philosophy",
        "philosopher",
        "epistemolog",
        "metaphysic",
        "phenomenolog",
        "ontology",
        "stoicism",
        "aristotle",
        "kantian",
        "confuc",
        "virtue ethics",
        "metaethics",
        "political philosophy",
        "philosophy of science",
        "aesthetics",
        "existential",
        "hawaiian epistemology",
        "phil-",
    ),
}

LAID_OUT = tuple(TOPIC_KEYS.keys()) + ("pantry",)


def tag_record(row: dict[str, Any]) -> list[str]:
    blob = " ".join(
        [
            str(row.get("title") or ""),
            str(row.get("abstract") or ""),
            str(row.get("query") or ""),
            str(row.get("source") or ""),
        ]
    ).lower()
    hits = [name for name, keys in TOPIC_KEYS.items() if any(k in blob for k in keys)]
    if str(row.get("query") or "").startswith("ctahr-harvest") and "gardening" not in hits:
        hits.append("gardening")
    q = str(row.get("query") or "")
    if q.startswith("phil-") and "philosophy" not in hits:
        hits.append("philosophy")
    if not hits:
        hits = ["uncategorized"]
    return sorted(set(hits))


def retag() -> dict[str, int]:
    data = _load()
    counts: dict[str, int] = {}
    rows = []
    for row in data.get("records") or []:
        if not isinstance(row, dict):
            continue
        row = dict(row)
        row["topics"] = tag_record(row)
        for t in row["topics"]:
            counts[t] = counts.get(t, 0) + 1
        rows.append(row)
    data["records"] = rows
    _save(data)
    return counts


def lookup(query: str, *, topic: str = "") -> list[dict[str, Any]]:
    q = (query or "").strip().lower()
    want = (topic or "").strip().lower()
    hits = []
    for r in records():
        topics = [str(t).lower() for t in (r.get("topics") or [])]
        if want and want not in topics:
            continue
        if q:
            blob = " ".join(
                [
                    str(r.get("id") or ""),
                    str(r.get("title") or ""),
                    str(r.get("abstract") or ""),
                    str(r.get("source") or ""),
                    str(r.get("authors") or ""),
                    " ".join(topics),
                ]
            ).lower()
            if q not in blob:
                continue
        hits.append(r)
    return hits[:40]


def scholarspace(query: str, *, size: int = 15, scope: str | None = None) -> list[dict[str, Any]]:
    params = {"query": query, "size": str(size), "page": "0"}
    if scope:
        params["scope"] = scope
    url = (
        "https://scholarspace.manoa.hawaii.edu/server/api/discover/search/objects?"
        + urllib.parse.urlencode(params)
    )
    data = _get(url)
    objs = (
        ((data.get("_embedded") or {}).get("searchResult") or {}).get("_embedded") or {}
    ).get("objects") or []
    out: list[dict[str, Any]] = []
    for wrap in objs:
        item = (wrap.get("_embedded") or {}).get("indexableObject") or {}
        if item.get("type") and item.get("type") != "item":
            continue
        uuid = str(item.get("uuid") or "")
        if not uuid:
            continue
        meta = item.get("metadata") or {}
        handle = str(item.get("handle") or "") or _first_md(meta, "dc.identifier.uri")
        out.append(
            {
                "id": f"scholarspace:{uuid}",
                "source": "scholarspace",
                "peer_review": "ir-item",
                "is_oa": True,
                "title": str(item.get("name") or _first_md(meta, "dc.title"))[:280],
                "year": _first_md(meta, "dc.date.issued")[:10],
                "authors": _authors_md(meta),
                "abstract": _first_md(meta, "dc.description.abstract")[:ABS_CAP],
                "url": f"https://scholarspace.manoa.hawaii.edu/items/{uuid}",
                "handle": handle,
                "license": _first_md(meta, "dc.rights", "dc.rights.uri") or "scholarspace-item",
                "pdf_url": "",
                "query": query,
            }
        )
    return out


def openalex(query: str, *, size: int = 10) -> list[dict[str, Any]]:
    params = {
        "search": query,
        "filter": "is_oa:true",
        "per_page": str(min(size, 25)),
    }
    url = "https://api.openalex.org/works?" + urllib.parse.urlencode(params)
    data = _get(url)
    out: list[dict[str, Any]] = []
    for w in data.get("results") or []:
        if not isinstance(w, dict):
            continue
        loc = w.get("best_oa_location") or {}
        oa = w.get("open_access") or {}
        if not oa.get("is_oa"):
            continue
        wid = str(w.get("id") or "").rsplit("/", 1)[-1]
        authors = []
        for a in (w.get("authorships") or [])[:8]:
            name = ((a.get("author") or {}).get("display_name")) if isinstance(a, dict) else ""
            if name:
                authors.append(str(name))
        out.append(
            {
                "id": f"openalex:{wid}",
                "source": "openalex",
                "peer_review": "journal-or-other",
                "is_oa": True,
                "oa_status": oa.get("oa_status"),
                "title": str(w.get("display_name") or "")[:280],
                "year": str(w.get("publication_year") or ""),
                "authors": "; ".join(authors)[:240],
                "abstract": "",
                "url": str(w.get("id") or ""),
                "doi": (w.get("doi") or "") or "",
                "pdf_url": loc.get("pdf_url") or oa.get("oa_url") or "",
                "license": loc.get("license") or "",
                "query": query,
            }
        )
    return out


def europepmc(query: str, *, size: int = 10) -> list[dict[str, Any]]:
    q = f"{query} OPEN_ACCESS:y"
    params = {"query": q, "format": "json", "pageSize": str(min(size, 25))}
    url = (
        "https://www.ebi.ac.uk/europepmc/webservices/rest/search?"
        + urllib.parse.urlencode(params)
    )
    data = _get(url)
    out: list[dict[str, Any]] = []
    for w in ((data.get("resultList") or {}).get("result") or []):
        if not isinstance(w, dict):
            continue
        if str(w.get("isOpenAccess") or "").upper() not in {"Y", "TRUE"}:
            continue
        pmcid = str(w.get("pmcid") or "")
        pmid = str(w.get("pmid") or "")
        rid = pmcid or pmid or str(w.get("id") or "")
        pdf = ""
        if pmcid:
            pdf = f"https://www.ncbi.nlm.nih.gov/pmc/articles/{pmcid}/pdf/"
        out.append(
            {
                "id": f"epmc:{rid}",
                "source": "europepmc",
                "peer_review": "journal",
                "is_oa": True,
                "title": str(w.get("title") or "")[:280],
                "year": str(w.get("pubYear") or ""),
                "authors": str(w.get("authorString") or "")[:240],
                "abstract": "",
                "url": f"https://europepmc.org/article/MED/{pmid}" if pmid else "",
                "doi": str(w.get("doi") or ""),
                "pdf_url": pdf,
                "license": "europepmc-oa",
                "query": query,
            }
        )
    return out


def arxiv(query: str, *, size: int = 8) -> list[dict[str, Any]]:
    params = {
        "search_query": f"all:{query}",
        "start": "0",
        "max_results": str(min(size, 20)),
    }
    url = "http://export.arxiv.org/api/query?" + urllib.parse.urlencode(params)
    root = _get(url, xml=True)
    out: list[dict[str, Any]] = []
    for entry in root.findall(f"{ATOM}entry"):
        eid = (entry.findtext(f"{ATOM}id") or "").strip()
        aid = eid.rsplit("/", 1)[-1]
        pdf = ""
        for link in entry.findall(f"{ATOM}link"):
            if link.get("title") == "pdf" or link.get("type") == "application/pdf":
                pdf = link.get("href") or ""
        out.append(
            {
                "id": f"arxiv:{aid}",
                "source": "arxiv",
                "peer_review": "preprint",
                "is_oa": True,
                "title": re.sub(r"\s+", " ", entry.findtext(f"{ATOM}title") or "").strip()[:280],
                "year": (entry.findtext(f"{ATOM}published") or "")[:4],
                "authors": "; ".join(
                    a.findtext(f"{ATOM}name") or ""
                    for a in entry.findall(f"{ATOM}author")
                )[:240],
                "abstract": re.sub(
                    r"\s+", " ", entry.findtext(f"{ATOM}summary") or ""
                ).strip()[:ABS_CAP],
                "url": eid,
                "pdf_url": pdf,
                "license": "arxiv-preprint",
                "query": query,
            }
        )
    return out


def gbif(query: str) -> list[dict[str, Any]]:
    url = "https://api.gbif.org/v1/species/match?" + urllib.parse.urlencode(
        {"name": query}
    )
    w = _get(url)
    if not isinstance(w, dict) or not w.get("usageKey"):
        return []
    key = w.get("usageKey")
    return [
        {
            "id": f"gbif:{key}",
            "source": "gbif",
            "peer_review": "occurrence-taxonomy",
            "is_oa": True,
            "title": str(w.get("scientificName") or query)[:280],
            "year": "",
            "authors": "",
            "abstract": json.dumps(
                {
                    "rank": w.get("rank"),
                    "status": w.get("status"),
                    "confidence": w.get("confidence"),
                    "family": w.get("family"),
                    "kingdom": w.get("kingdom"),
                },
                ensure_ascii=False,
            ),
            "url": f"https://www.gbif.org/species/{key}",
            "license": "gbif-taxonomy",
            "query": query,
        }
    ]


SOURCES = {
    "scholarspace": scholarspace,
    "openalex": openalex,
    "europepmc": europepmc,
    "arxiv": arxiv,
    "gbif": gbif,
}


def search_live(query: str, src: str | None = None) -> list[dict[str, Any]]:
    names = [src] if src else ["scholarspace", "openalex", "europepmc", "arxiv"]
    out: list[dict[str, Any]] = []
    for name in names:
        fn = SOURCES.get(name)
        if not fn:
            continue
        try:
            if name == "scholarspace":
                out.extend(fn(query, size=12))
                time.sleep(SLEEP)
                out.extend(fn(query, size=8, scope=CTAHR))
            elif name == "gbif":
                out.extend(fn(query))
            else:
                out.extend(fn(query))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, ET.ParseError) as exc:
            out.append(
                {
                    "id": f"error:{name}",
                    "source": name,
                    "title": f"{name} failed",
                    "abstract": str(exc)[:200],
                    "is_oa": False,
                }
            )
        time.sleep(SLEEP)
    return [r for r in out if r.get("id") and not str(r.get("id")).startswith("error:")]


def fetch_pdf(row: dict[str, Any]) -> str | None:
    url = str(row.get("pdf_url") or "")
    rid = str(row.get("id") or "")
    if rid.startswith("scholarspace:") and not url:
        uuid = rid.split(":", 1)[-1]
        try:
            url = ss_pdf_url(uuid)
            time.sleep(SLEEP)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
            url = ""
        if url:
            row["pdf_url"] = url
    if not url or not row.get("is_oa"):
        return None
    dest_dir = DUMP / "pdf"
    dest_dir.mkdir(parents=True, exist_ok=True)
    safe = re.sub(r"[^a-zA-Z0-9._-]+", "-", rid)[:80]
    dest = dest_dir / f"{safe}.pdf"
    if dest.is_file() and dest.stat().st_size > 1000:
        row["pdf_path"] = str(dest)
        row["pdf_bytes"] = dest.stat().st_size
        return str(dest)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=90) as resp:
            blob = resp.read(PDF_CAP)
    except (urllib.error.URLError, TimeoutError, OSError):
        return None
    if blob[:4] != b"%PDF" and b"%PDF" not in blob[:32]:
        return None
    dest.write_bytes(blob)
    row["pdf_path"] = str(dest)
    row["pdf_bytes"] = dest.stat().st_size
    return str(dest)


def harvest_ctahr(*, pages: int = 12, size: int = 20, start_page: int = 0) -> list[dict[str, Any]]:
    return harvest_scope(CTAHR, pages=pages, size=size, start_page=start_page, label="ctahr-harvest")


def harvest_scope(
    scope: str,
    *,
    pages: int = 12,
    size: int = 20,
    start_page: int = 0,
    label: str = "scope-harvest",
    query: str = "*",
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    start = max(0, start_page)
    for page in range(start, start + max(1, pages)):
        params = {
            "query": query,
            "size": str(size),
            "page": str(page),
            "scope": scope,
        }
        url = (
            "https://scholarspace.manoa.hawaii.edu/server/api/discover/search/objects?"
            + urllib.parse.urlencode(params)
        )
        try:
            data = _get(url)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
            break
        objs = (
            ((data.get("_embedded") or {}).get("searchResult") or {}).get("_embedded")
            or {}
        ).get("objects") or []
        if not objs:
            break
        chunk = []
        for wrap in objs:
            item = (wrap.get("_embedded") or {}).get("indexableObject") or {}
            uuid = str(item.get("uuid") or "")
            if not uuid:
                continue
            meta = item.get("metadata") or {}
            handle = str(item.get("handle") or "") or _first_md(meta, "dc.identifier.uri")
            chunk.append(
                {
                    "id": f"scholarspace:{uuid}",
                    "source": "scholarspace",
                    "peer_review": "ir-item",
                    "is_oa": True,
                    "title": str(item.get("name") or _first_md(meta, "dc.title"))[:280],
                    "year": _first_md(meta, "dc.date.issued")[:10],
                    "authors": _authors_md(meta),
                    "abstract": _first_md(meta, "dc.description.abstract")[:ABS_CAP],
                    "url": f"https://scholarspace.manoa.hawaii.edu/items/{uuid}",
                    "handle": handle,
                    "license": _first_md(meta, "dc.rights", "dc.rights.uri")
                    or "scholarspace-item",
                    "pdf_url": "",
                    "query": f"{label} p{page}",
                }
            )
        out.extend(chunk)
        time.sleep(SLEEP)
    return out


def harvest_openalex(queries: list[str], *, pages: int = 3, per_page: int = 25) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for query in queries:
        cursor = "*"
        for _ in range(max(1, pages)):
            params = {
                "search": query,
                "filter": "is_oa:true",
                "per_page": str(min(per_page, 50)),
                "cursor": cursor,
            }
            url = "https://api.openalex.org/works?" + urllib.parse.urlencode(params)
            try:
                data = _get(url)
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
                break
            out.extend(openalex_rows(data, query))
            cursor = str((data.get("meta") or {}).get("next_cursor") or "")
            if not cursor:
                break
            time.sleep(SLEEP)
        time.sleep(SLEEP)
    return out


def openalex_rows(data: dict[str, Any], query: str) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for w in data.get("results") or []:
        if not isinstance(w, dict):
            continue
        loc = w.get("best_oa_location") or {}
        oa = w.get("open_access") or {}
        if not oa.get("is_oa"):
            continue
        wid = str(w.get("id") or "").rsplit("/", 1)[-1]
        authors = []
        for a in (w.get("authorships") or [])[:8]:
            name = ((a.get("author") or {}).get("display_name")) if isinstance(a, dict) else ""
            if name:
                authors.append(str(name))
        out.append(
            {
                "id": f"openalex:{wid}",
                "source": "openalex",
                "peer_review": "journal-or-other",
                "is_oa": True,
                "oa_status": oa.get("oa_status"),
                "title": str(w.get("display_name") or "")[:280],
                "year": str(w.get("publication_year") or ""),
                "authors": "; ".join(authors)[:240],
                "abstract": "",
                "url": str(w.get("id") or ""),
                "doi": (w.get("doi") or "") or "",
                "pdf_url": loc.get("pdf_url") or oa.get("oa_url") or "",
                "license": loc.get("license") or "",
                "query": query,
            }
        )
    return out


SEED = [
    ("scholarspace", "kalo taro Hawaiʻi"),
    ("scholarspace", "breadfruit ulu agroforestry"),
    ("scholarspace", "cover crop Hawaiʻi"),
    ("openalex", "Hawaiian taro Colocasia"),
    ("openalex", "Hawaiʻi agroforestry"),
    ("europepmc", "Hawaii taro Colocasia"),
    ("arxiv", "orographic rainfall Hawaii"),
    ("gbif", "Colocasia esculenta"),
    ("gbif", "Artocarpus altilis"),
]

OA_QUERIES = [
    "Hawaiian taro Colocasia",
    "Hawaiʻi agroforestry",
    "Hawaii cover crop",
    "breadfruit Artocarpus Hawaii",
    "Hawaii rainfall orographic",
    "taro wetland Hawaii",
    "Pacific Islands multi-story cropping",
]


def _tree_bytes(path: Path) -> int:
    if not path.exists():
        return 0
    if path.is_file():
        return path.stat().st_size
    return sum(f.stat().st_size for f in path.rglob("*") if f.is_file())


def organize_pdfs() -> dict[str, int]:
    """Hardlink each saved PDF into DUMP/by-topic/<topic>/."""
    linked = 0
    skipped = 0
    for row in records():
        src = Path(str(row.get("pdf_path") or ""))
        if not src.is_file():
            continue
        topics = [str(t) for t in (row.get("topics") or [])] or ["uncategorized"]
        for topic in topics:
            dest_dir = DUMP / "by-topic" / topic
            dest_dir.mkdir(parents=True, exist_ok=True)
            dest = dest_dir / src.name
            if dest.exists():
                skipped += 1
                continue
            try:
                os.link(src, dest)
            except OSError:
                import shutil

                shutil.copy2(src, dest)
            linked += 1
    return {"linked": linked, "already": skipped}


def pull_pdfs(*, limit: int = 80) -> dict[str, Any]:
    saved: list[str] = []
    fail = 0
    skipped = 0
    touched: list[dict[str, Any]] = []
    for row in records():
        if len(saved) >= limit:
            break
        if row.get("source") == "gbif":
            skipped += 1
            continue
        if row.get("pdf_path") and Path(str(row.get("pdf_path"))).is_file():
            skipped += 1
            continue
        path = fetch_pdf(row)
        if path:
            saved.append(path)
            touched.append(row)
        else:
            fail += 1
        time.sleep(SLEEP)
    if touched:
        upsert(touched)
    return {
        "saved": len(saved),
        "fail": fail,
        "already": skipped,
        "pdf_bytes": _tree_bytes(DUMP / "pdf"),
        "sample": saved[:12],
    }


def dump_status() -> dict[str, Any]:
    rows = records()
    by: dict[str, int] = {}
    topics: dict[str, int] = {}
    with_pdf = 0
    for r in rows:
        src = str(r.get("source") or "unknown")
        by[src] = by.get(src, 0) + 1
        for t in r.get("topics") or []:
            topics[str(t)] = topics.get(str(t), 0) + 1
        if r.get("pdf_path") and Path(str(r.get("pdf_path"))).is_file():
            with_pdf += 1
    pdf_n = 0
    pdf_dir = DUMP / "pdf"
    if pdf_dir.is_dir():
        pdf_n = sum(1 for f in pdf_dir.glob("*.pdf") if f.is_file())
    return {
        "name": "Root Record Registry",
        "records": len(rows),
        "by_source": by,
        "by_topic": topics,
        "catalog_with_pdf_path": with_pdf,
        "pdf_files": pdf_n,
        "dump_bytes": _tree_bytes(DUMP),
        "pdf_bytes": _tree_bytes(pdf_dir),
        "catalog_bytes": CATALOG.stat().st_size if CATALOG.is_file() else 0,
        "dump": str(DUMP),
        "openalex_full_snapshot": "not pulled (API harvest only)",
    }


def sources() -> dict[str, Any]:
    return {
        "scholarspace": "https://scholarspace.manoa.hawaii.edu/server/api",
        "ctahr_community": CTAHR,
        "openalex": "https://api.openalex.org/works?filter=is_oa:true",
        "europepmc": "https://www.ebi.ac.uk/europepmc/webservices/rest/search",
        "arxiv": "http://export.arxiv.org/api/query",
        "gbif": "https://api.gbif.org/v1/species/match",
        "media": str(DUMP),
        "catalog": str(CATALOG),
        "records": len(records()),
        "no": ["sci-hub", "libgen", "paywalled PDF", "OpenAlex paid content archive"],
    }


def _dump(obj: Any) -> int:
    print(json.dumps(obj, indent=2, ensure_ascii=False))
    return 0


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if not args or args[0] in {"help", "-h", "--help"}:
        print(
            "research.py sources|status|topics|tag|lookup [--topic NAME] Q|search [--src NAME] [--pdf] Q|seed [--pdf]|harvest-ctahr|harvest-openalex|pull-pdfs [N]",
            file=sys.stderr,
        )
        return 0
    cmd = args[0]
    if cmd == "sources":
        return _dump(sources())
    if cmd == "lookup":
        live = "--live" in args
        rest = [a for a in args[1:] if a != "--live"]
        topic = ""
        if rest and rest[0] == "--topic" and len(rest) >= 2:
            topic = rest[1]
            rest = rest[2:]
        q = " ".join(rest).strip()
        hits = lookup(q, topic=topic)
        if not hits and live and q:
            rows = search_live(q)
            upsert(rows)
            hits = lookup(q, topic=topic)
        return _dump({"registry": "Root Record Registry", "query": q, "topic": topic, "n": len(hits), "records": hits})
    if cmd == "tag":
        return _dump({"ok": True, "counts": retag()})
    if cmd == "topics":
        return _dump({"registry": "Root Record Registry", "topics": list(LAID_OUT), "counts": retag() if "--apply" in args else None})
    if cmd == "search":
        pdf = "--pdf" in args
        src = None
        rest = [a for a in args[1:] if a != "--pdf"]
        if rest and rest[0] == "--src" and len(rest) >= 3:
            src = rest[1]
            rest = rest[2:]
        q = " ".join(rest).strip()
        if not q:
            return _dump({"ok": False, "error": "empty_query"})
        rows = search_live(q, src=src)
        if src == "gbif":
            rows = gbif(q)
        added = upsert(rows)
        saved = []
        if pdf:
            for row in rows[:4]:
                path = fetch_pdf(row)
                if path:
                    saved.append(path)
        return _dump({"query": q, "added": added, "n": len(rows), "pdf": saved, "records": rows})
    if cmd == "seed":
        pdf = "--pdf" in args
        all_rows: list[dict[str, Any]] = []
        for src, q in SEED:
            try:
                if src == "scholarspace":
                    all_rows.extend(scholarspace(q, size=10))
                    time.sleep(SLEEP)
                    all_rows.extend(scholarspace(q, size=8, scope=CTAHR))
                elif src == "gbif":
                    all_rows.extend(gbif(q))
                else:
                    all_rows.extend(SOURCES[src](q))
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, ET.ParseError) as exc:
                all_rows.append(
                    {
                        "id": f"error:{src}:{q}",
                        "source": src,
                        "title": "seed failed",
                        "abstract": str(exc)[:200],
                    }
                )
            time.sleep(SLEEP)
        clean = [r for r in all_rows if r.get("id") and not str(r["id"]).startswith("error:")]
        added = upsert(clean)
        saved = []
        if pdf:
            for row in clean[:6]:
                path = fetch_pdf(row)
                if path:
                    saved.append(path)
        return _dump({"ok": True, "added": added, "n": len(clean), "catalog": len(records()), "pdf": saved})
    if cmd == "status":
        return _dump(dump_status())
    if cmd == "harvest-ctahr":
        pages = 15
        if len(args) > 1 and args[1].isdigit():
            pages = int(args[1])
        rows = harvest_ctahr(pages=pages, size=20)
        added = upsert(rows)
        return _dump({"ok": True, "pages": pages, "n": len(rows), "added": added, "catalog": len(records())})
    if cmd == "harvest-openalex":
        rows = harvest_openalex(OA_QUERIES, pages=3, per_page=25)
        added = upsert(rows)
        return _dump({"ok": True, "n": len(rows), "added": added, "catalog": len(records())})
    if cmd in {"pull-pdfs", "pull-pdf"}:
        limit = 80
        if len(args) > 1 and args[1].isdigit():
            limit = int(args[1])
        return _dump(pull_pdfs(limit=limit))
    if cmd == "organize":
        return _dump(organize_pdfs())
    return _dump({"ok": False, "error": "usage"})


if __name__ == "__main__":
    raise SystemExit(main())
