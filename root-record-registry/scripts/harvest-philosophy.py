#!/usr/bin/env python3
"""Root Record Registry — philosophy harvest. OA only. Own state file.

UH Philosophy + Religion communities, then OpenAlex subject queries.
PDFs tagged philosophy and sorted into by-topic/philosophy/.

  python3 ~/.ollama/skills/root-record-registry/scripts/harvest-philosophy.py --minutes 60
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import research as r  # noqa: E402

STATE_PATH = r.SKILL / "store" / "harvest-philosophy-state.json"
MIN_FREE = 8 * 1024 * 1024 * 1024
OA_QUERIES = [
    "Hawaiian epistemology philosophy",
    "philosophy of aloha ʻāina",
    "Indigenous Hawaiian philosophy",
    "epistemology open access",
    "metaphysics ontology philosophy",
    "virtue ethics Aristotle",
    "political philosophy justice",
    "philosophy of science",
    "phenomenology Husserl",
    "stoicism ethics",
    "Confucian philosophy",
    "aesthetics philosophy art",
    "philosophy of mind",
    "logic philosophy (not computer science)",
    "environmental philosophy ethics land",
]
SS_SEARCH = [
    "philosophy",
    "epistemology",
    "ethics",
    "Hawaiian philosophy",
]


def log(msg: str) -> None:
    print(time.strftime("%H:%M:%S"), msg, flush=True)


def load_state() -> dict:
    if STATE_PATH.is_file():
        try:
            data = json.loads(STATE_PATH.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                return data
        except (OSError, json.JSONDecodeError):
            pass
    return {
        "phil_page": 0,
        "rel_page": 0,
        "oa_i": 0,
        "ss_i": 0,
        "cycles": 0,
        "pdf_fail": [],
        "phil_done": False,
        "rel_done": False,
    }


def save_state(state: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = STATE_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    tmp.replace(STATE_PATH)


def is_phil(row: dict) -> bool:
    topics = [str(t).lower() for t in (row.get("topics") or [])]
    if "philosophy" in topics:
        return True
    q = str(row.get("query") or "").lower()
    blob = f"{row.get('title') or ''} {q}".lower()
    return q.startswith("phil-") or "philosoph" in blob


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--minutes", type=int, default=60)
    p.add_argument("--pdf-batch", type=int, default=15)
    p.add_argument("--pages", type=int, default=3)
    args = p.parse_args()
    deadline = time.time() + max(5, args.minutes) * 60
    state = load_state()
    fail_ids = set(str(x) for x in (state.get("pdf_fail") or []))
    log(
        f"philosophy harvest start minutes={args.minutes} catalog={len(r.records())}"
    )
    while time.time() < deadline:
        if shutil.disk_usage(str(r.DUMP)).free < MIN_FREE:
            log("stop: less than 8 GiB free")
            break
        if len(r.records()) >= r.MAX_RECORDS:
            log("stop: catalog at MAX_RECORDS")
            break
        cycle = int(state.get("cycles") or 0) + 1
        state["cycles"] = cycle
        log(f"cycle {cycle} remaining={int(deadline - time.time())}s")

        if not state.get("phil_done"):
            page = int(state.get("phil_page") or 0)
            rows = r.harvest_scope(
                r.PHIL,
                pages=args.pages,
                size=20,
                start_page=page,
                label="phil-uh-philosophy",
            )
            added = r.upsert(rows)
            log(f"  UH Philosophy p{page} n={len(rows)} added={added}")
            if not rows:
                state["phil_done"] = True
            else:
                state["phil_page"] = page + args.pages

        if not state.get("rel_done"):
            page = int(state.get("rel_page") or 0)
            rows = r.harvest_scope(
                r.RELIGION,
                pages=args.pages,
                size=20,
                start_page=page,
                label="phil-uh-religion",
            )
            added = r.upsert(rows)
            log(f"  UH Religion p{page} n={len(rows)} added={added}")
            if not rows:
                state["rel_done"] = True
            else:
                state["rel_page"] = page + args.pages

        si = int(state.get("ss_i") or 0) % len(SS_SEARCH)
        sq = SS_SEARCH[si]
        ss_rows = r.scholarspace(sq, size=15)
        for row in ss_rows:
            row["query"] = f"phil-search {sq}"
        state["ss_i"] = si + 1
        log(f"  scholarspace {sq!r} n={len(ss_rows)} added={r.upsert(ss_rows)}")

        qi = int(state.get("oa_i") or 0) % len(OA_QUERIES)
        q = OA_QUERIES[qi]
        oa_rows = r.harvest_openalex([q], pages=2, per_page=25)
        for row in oa_rows:
            row["query"] = f"phil-oa {q}"
        state["oa_i"] = qi + 1
        log(f"  openalex {q!r} n={len(oa_rows)} added={r.upsert(oa_rows)}")

        r.retag()
        saved = 0
        touched = []
        for row in r.records():
            if saved >= args.pdf_batch:
                break
            rid = str(row.get("id") or "")
            if rid in fail_ids or row.get("source") == "gbif":
                continue
            if not is_phil(row):
                continue
            if row.get("pdf_path") and Path(str(row.get("pdf_path"))).is_file():
                continue
            path = r.fetch_pdf(row)
            if path:
                saved += 1
                touched.append(row)
            else:
                fail_ids.add(rid)
            time.sleep(r.SLEEP)
        if touched:
            r.upsert(touched)
        log(f"  philosophy pdfs saved={saved} fail_ids={len(fail_ids)}")

        counts = r.retag()
        org = r.organize_pdfs()
        st = r.dump_status()
        log(
            f"  catalog={st.get('records')} philosophy={counts.get('philosophy', 0)} "
            f"pdf_files={st.get('pdf_files')} "
            f"dump_mib={round((st.get('dump_bytes') or 0)/1048576, 1)} organize={org}"
        )
        state["pdf_fail"] = sorted(fail_ids)[-4000:]
        save_state(state)
        time.sleep(1.5)

    save_state(state)
    log(f"done {json.dumps(r.dump_status())}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        log("interrupted")
        raise SystemExit(130)
