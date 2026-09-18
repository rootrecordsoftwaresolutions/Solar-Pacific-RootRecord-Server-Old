#!/usr/bin/env python3
"""Pull RootRecord datapacks from Telegram and decompress.

Runs at :10/:25/:40/:55 HST. Never uses SSH for data.

Catch-up: downloads *every* new rootrecord-*.zip since last offset (not only
the newest), archives each, and sets live/ to the newest applied pack.
Also applies any unread zips already sitting in store/incoming/.
"""
from __future__ import annotations

import hashlib
import json
import logging
import os
import shutil
import sys
import zipfile
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import httpx

logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)

HST = ZoneInfo("Pacific/Honolulu")
log = logging.getLogger("rr.ingest")

SKILL = Path(__file__).resolve().parents[1]
STORE = SKILL / "store"
INCOMING = STORE / "incoming"
LIVE = STORE / "live"
ARCHIVE = STORE / "archive"
STATE = STORE / "state"
ETC = SKILL / "local" / "etc"
API = "https://api.telegram.org"


def load_dotenv() -> None:
    path = ETC / "secrets.env"
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        k, v = k.strip(), v.strip().strip("'").strip('"')
        if k and k not in os.environ:
            os.environ[k] = v


def _token() -> str:
    return (
        os.environ.get("RR_DATAPACK_RECV_BOT_TOKEN")
        or os.environ.get("RR_DATAPACK_BOT_TOKEN")
        or ""
    ).strip()


def _chat() -> str:
    return (os.environ.get("RR_DATAPACK_CHAT_ID") or "").strip()


def _state() -> dict:
    p = STATE / "ingest.json"
    if not p.is_file():
        return {}
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def _save_state(data: dict) -> None:
    STATE.mkdir(parents=True, exist_ok=True)
    tmp = STATE / "ingest.json.tmp"
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(STATE / "ingest.json")


def _file_sha12(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:12]


def _pack_id_from_name(name: str) -> str:
    stem = name
    if stem.startswith("rootrecord-"):
        stem = stem[len("rootrecord-") :]
    if stem.endswith(".zip"):
        stem = stem[: -len(".zip")]
    return stem


def _applied_ids(st: dict) -> set[str]:
    ids = set(st.get("applied_pack_ids") or [])
    if st.get("last_ok_pack_id"):
        ids.add(str(st["last_ok_pack_id"]))
    return ids


def _download_file(client: httpx.Client, token: str, file_id: str, dest: Path) -> Path:
    fr = client.get(f"{API}/bot{token}/getFile", params={"file_id": file_id}, timeout=60.0)
    fr.raise_for_status()
    fdata = fr.json()
    if not fdata.get("ok"):
        raise RuntimeError(str(fdata)[:200])
    file_path = fdata["result"]["file_path"]
    url = f"{API}/file/bot{token}/{file_path}"
    dest.parent.mkdir(parents=True, exist_ok=True)
    with client.stream("GET", url, timeout=600.0) as resp:
        resp.raise_for_status()
        with dest.open("wb") as out:
            for chunk in resp.iter_bytes():
                out.write(chunk)
    return dest


def fetch_all_new_packs(client: httpx.Client, token: str, chat_id: str, st: dict) -> list[Path]:
    """Download every new rootrecord zip since last telegram offset. Returns paths sorted oldest→newest."""
    INCOMING.mkdir(parents=True, exist_ok=True)
    chat_ids = {str(chat_id)}
    if chat_id.startswith("-") and not chat_id.startswith("-100"):
        chat_ids.add("-100" + chat_id.lstrip("-"))

    offset = int(st.get("telegram_offset") or 0)
    found: list[tuple[int, str, str]] = []  # date, file_id, name
    max_update = offset
    # Page until empty (Telegram returns ≤100)
    while True:
        params: dict = {
            "limit": 100,
            "timeout": 0,
            "allowed_updates": json.dumps(["channel_post", "message"]),
        }
        if offset:
            params["offset"] = offset
        r = client.get(f"{API}/bot{token}/getUpdates", params=params, timeout=60.0)
        r.raise_for_status()
        data = r.json()
        if not data.get("ok"):
            raise RuntimeError(str(data)[:200])
        batch = data.get("result") or []
        if not batch:
            break
        for upd in batch:
            uid = int(upd.get("update_id") or 0)
            max_update = max(max_update, uid + 1)
            msg = upd.get("channel_post") or upd.get("message") or {}
            chat = msg.get("chat") or {}
            if str(chat.get("id") or "") not in chat_ids:
                continue
            doc = msg.get("document") or {}
            name = str(doc.get("file_name") or "")
            if not name.startswith("rootrecord-") or not name.endswith(".zip"):
                continue
            if "-part" in name:
                continue
            file_id = doc.get("file_id")
            date = int(msg.get("date") or 0)
            if file_id:
                found.append((date, str(file_id), name))
        offset = max(u.get("update_id", 0) for u in batch) + 1
        if len(batch) < 100:
            break

    # Persist offset only after successful scan (downloads below)
    st["telegram_offset"] = max_update

    applied = _applied_ids(st)
    paths: list[Path] = []
    for date, file_id, name in sorted(found, key=lambda t: (t[0], t[2])):
        pack_id = _pack_id_from_name(name)
        dest = INCOMING / name
        if pack_id in applied and dest.is_file():
            continue
        if dest.is_file() and pack_id in applied:
            continue
        if not dest.is_file():
            log.info("downloading pack %s", name)
            _download_file(client, token, file_id, dest)
        paths.append(dest)

    # Also any local drops not yet applied
    for dest in sorted(INCOMING.glob("rootrecord-*.zip")):
        if "-part" in dest.name:
            continue
        pack_id = _pack_id_from_name(dest.name)
        if pack_id in applied:
            continue
        if dest not in paths:
            paths.append(dest)

    paths.sort(key=lambda p: p.name)
    return paths


def decompress_to_archive(zip_path: Path) -> Path:
    ARCHIVE.mkdir(parents=True, exist_ok=True)
    pack_id = _pack_id_from_name(zip_path.name)
    dest = ARCHIVE / pack_id
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(dest)
    return dest


def set_live_from_archive(arch: Path) -> Path:
    LIVE.mkdir(parents=True, exist_ok=True)
    for child in list(LIVE.iterdir()):
        if child.name == ".keep":
            continue
        if child.is_dir():
            shutil.rmtree(child)
        else:
            child.unlink()
    for child in arch.iterdir():
        target = LIVE / child.name
        if child.is_dir():
            shutil.copytree(child, target)
        else:
            shutil.copy2(child, target)
    return LIVE


def apply_pack(zip_path: Path) -> dict:
    arch = decompress_to_archive(zip_path)
    set_live_from_archive(arch)
    return {
        "pack_id": _pack_id_from_name(zip_path.name),
        "sha256_12": _file_sha12(zip_path),
        "bytes": zip_path.stat().st_size,
        "archive": str(arch),
    }


def run() -> dict:
    load_dotenv()
    for d in (STORE, INCOMING, LIVE, ARCHIVE, STATE):
        d.mkdir(parents=True, exist_ok=True)
    token, chat = _token(), _chat()
    st = _state()
    now = datetime.now(HST)
    out: dict = {"ok": False, "hst": now.isoformat(), "action": None, "applied": []}

    if not token or not chat:
        out["detail"] = "RR_DATAPACK_RECV_BOT_TOKEN / RR_DATAPACK_CHAT_ID missing"
        _save_state({**st, "last_ingest": out})
        return out

    with httpx.Client() as client:
        try:
            packs = fetch_all_new_packs(client, token, chat, st)
        except Exception as exc:
            out["detail"] = f"fetch failed: {exc}"[:400]
            out["action"] = "stale_pack"
            _save_state({**st, "last_ingest": out})
            return out

    if not packs:
        out["ok"] = True
        out["action"] = "stale_pack"
        out["detail"] = "no new packs; keeping last live tree"
        out["last_ok_pack_id"] = st.get("last_ok_pack_id")
        _save_state({**st, "last_ingest": out, "telegram_offset": st.get("telegram_offset")})
        return out

    applied_ids = _applied_ids(st)
    last_meta = None
    for pack in packs:
        pack_id = _pack_id_from_name(pack.name)
        if pack_id in applied_ids:
            continue
        try:
            meta = apply_pack(pack)
        except Exception as exc:
            out["detail"] = f"apply failed {pack.name}: {exc}"[:400]
            _save_state({**st, "last_ingest": out, "telegram_offset": st.get("telegram_offset")})
            return out
        applied_ids.add(pack_id)
        out["applied"].append(meta)
        last_meta = meta
        log.info("applied pack %s", pack_id)

    if last_meta is None:
        out["ok"] = True
        out["action"] = "duplicate_ignored"
        _save_state({**st, "last_ingest": out, "telegram_offset": st.get("telegram_offset")})
        return out

    out.update(
        {
            "ok": True,
            "action": "ingested",
            "pack_id": last_meta["pack_id"],
            "sha256_12": last_meta["sha256_12"],
            "bytes": last_meta["bytes"],
            "live": str(LIVE),
            "count": len(out["applied"]),
        }
    )
    _save_state(
        {
            "last_ok_pack_id": last_meta["pack_id"],
            "last_ok_at": now.isoformat(),
            "last_sha256_12": last_meta["sha256_12"],
            "applied_pack_ids": sorted(applied_ids)[-200:],  # cap growth
            "telegram_offset": st.get("telegram_offset"),
            "last_ingest": out,
        }
    )
    (STATE / "prep-needed").write_text(last_meta["pack_id"] + "\n", encoding="utf-8")
    return out


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    result = run()
    print(json.dumps(result, indent=2))
    if not result.get("ok") and result.get("action") != "stale_pack":
        sys.exit(1)


if __name__ == "__main__":
    main()
