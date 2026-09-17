#!/usr/bin/env python3
"""Root Record Registry — run for ~1 hour: download OA, tag, sort by topic.

OA only. Resumes from store/harvest-state.json. Logs to stdout.

  python3 ~/.ollama/skills/root-record-registry/scripts/harvest-hour.py --minutes 60
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import research as r  # noqa: E402

STATE_PATH = r.SKILL / "store" / "harvest-state.json"
MIN_FREE = 8 * 1024 * 1024 * 1024
EXTRA_OA = [
    "Hawaiian taro Colocasia esculenta",
    "Hawaiʻi agroforestry breadfruit",
    "CTAHR cover crop sunn hemp",
    "Hawaii coffee shade agroforestry",
    "macadamia Hawaiʻi",
    "Hawaiian Kingdom agriculture kalo",
    "Giambelluca rainfall Hawaiʻi",
    "Pacific Islands mixed agroforest",
    "loʻi kalo wetland Hawaii",
    "Artocarpus altilis Hawaii",
]
EPMC = [
    "Hawaii taro OPEN_ACCESS:y",
    "Hawaii agroforestry OPEN_ACCESS:y",
    "Colocasia esculenta Hawaii OPEN_ACCESS:y",
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
        "ctahr_page": 12,
        "oa_i": 0,
        "epmc_i": 0,
        "cycles": 0,
        "pdf_fail": [],
        "ctahr_done": False,
    }


def save_state(state: dict) -> None:
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = STATE_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    tmp.replace(STATE_PATH)


def free_bytes() -> int:
    return shutil.disk_usage(str(r.DUMP)).free


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--minutes", type=int, default=60)
    p.add_argument("--pdf-batch", type=int, default=20)
    p.add_argument("--ctahr-pages", type=int, default=4)
    args = p.parse_args()
    deadline = time.time() + max(5, args.minutes) * 60
    state = load_state()
    fail_ids = set(str(x) for x in (state.get("pdf_fail") or []))
    log(
        f"Root Record Registry harvest start minutes={args.minutes} "
        f"catalog={len(r.records())} ctahr_page={state.get('ctahr_page')}"
    )
    while time.time() < deadline:
        if free_bytes() < MIN_FREE:
            log("stop: less than 8 GiB free")
            break
        if len(r.records()) >= r.MAX_RECORDS:
            log("stop: catalog at MAX_RECORDS")
            break
        cycle = int(state.get("cycles") or 0) + 1
        state["cycles"] = cycle
        log(f"cycle {cycle} remaining={int(deadline - time.time())}s")

        if not state.get("ctahr_done"):
            page = int(state.get("ctahr_page") or 0)
            rows = r.harvest_ctahr(pages=args.ctahr_pages, size=20, start_page=page)
            added = r.upsert(rows)
            log(f"  ctahr page {page}+{args.ctahr_pages} n={len(rows)} added={added}")
            if not rows:
                state["ctahr_done"] = True
            else:
                state["ctahr_page"] = page + args.ctahr_pages

        qi = int(state.get("oa_i") or 0) % len(EXTRA_OA)
        q = EXTRA_OA[qi]
        oa_rows = r.harvest_openalex([q], pages=2, per_page=25)
        oa_added = r.upsert(oa_rows)
        state["oa_i"] = qi + 1
        log(f"  openalex {q!r} n={len(oa_rows)} added={oa_added}")

        ei = int(state.get("epmc_i") or 0) % len(EPMC)
        eq = EPMC[ei].replace(" OPEN_ACCESS:y", "")
        try:
            epmc_rows = r.europepmc(eq, size=15)
        except Exception as exc:  # noqa: BLE001
            epmc_rows = []
            log(f"  epmc fail {exc}")
        epmc_added = r.upsert(epmc_rows)
        state["epmc_i"] = ei + 1
        log(f"  epmc {eq!r} n={len(epmc_rows)} added={epmc_added}")

        # Pull PDFs; skip known fails this process.
        saved = 0
        touched = []
        for row in r.records():
            if saved >= args.pdf_batch:
                break
            rid = str(row.get("id") or "")
            if rid in fail_ids or row.get("source") == "gbif":
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
        log(f"  pdfs saved={saved} fail_ids={len(fail_ids)}")

        counts = r.retag()
        org = r.organize_pdfs()
        st = r.dump_status()
        log(
            f"  catalog={st.get('records')} pdf_files={st.get('pdf_files')} "
            f"dump_mib={round((st.get('dump_bytes') or 0)/1048576, 1)} "
            f"topics={counts} organize={org}"
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
