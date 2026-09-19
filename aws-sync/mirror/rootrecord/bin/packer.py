#!/usr/bin/env python3
"""Zip AWS work tree → Telegram transfer channel → wipe work.

Aligned for local ingest at :10/:25/:40/:55 HST.
Wipe only after successful send. Refuse to pack if previous wipe failed.
"""
from __future__ import annotations

import hashlib
import json
import logging
import os
import shutil
import subprocess
import sys
import time
import zipfile
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import ETC, LOGS, OUT, ROOT, WORK, ensure_dirs, load_dotenv, now_hst, write_json

log = logging.getLogger("rr.packer")
API = "https://api.telegram.org"
MAX_BYTES = 2 * 1024 * 1024 * 1024  # 2 GiB
WIPE_LOCK = ETC / "wipe-failed"
PACK_MINUTES = {10, 25, 40, 55}


def _token() -> str:
    # Sender bot (posts zips). Prefer explicit send token.
    return (
        os.environ.get("RR_DATAPACK_SEND_BOT_TOKEN")
        or os.environ.get("RR_DATAPACK_BOT_TOKEN")
        or os.environ.get("RR_TELEGRAM_BOT_TOKEN")
        or ""
    ).strip()


def _channel() -> str:
    return (os.environ.get("RR_DATAPACK_CHAT_ID") or "").strip()


def _content_hash(root: Path) -> str:
    h = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        if path.name == ".keep":
            continue
        h.update(str(path.relative_to(root)).encode())
        h.update(path.read_bytes())
    return h.hexdigest()[:12]


def _has_payload(root: Path) -> bool:
    for path in root.rglob("*"):
        if path.is_file() and path.name != ".keep":
            return True
    return False


def _arcname_for(path: Path, stamp: str) -> str:
    """Pack for local with timestamped names. Skip .py entirely."""
    rel = path.relative_to(WORK)
    parent = str(rel.parent) if str(rel.parent) != "." else ""
    name = path.name
    stamped = f"{stamp}-{name}"
    return f"{parent}/{stamped}" if parent else stamped


def build_zip() -> tuple[Path, dict]:
    ensure_dirs()
    OUT.mkdir(parents=True, exist_ok=True)
    # Fresh sysmon + chronological assets into work/ before hashing
    try:
        from collect_sysmon import collect

        collect()
    except Exception as exc:
        log.warning("sysmon collect failed: %s", exc)
    try:
        from dropin_supervisor import sync_assets, ensure_layout

        ensure_layout()
        sync_assets()
    except Exception as exc:
        log.warning("assets sync failed: %s", exc)

    if not _has_payload(WORK):
        raise RuntimeError("work tree empty — nothing to pack")
    digest = _content_hash(WORK)
    stamp = now_hst().strftime("%Y%m%d-%H%M")
    name = f"rootrecord-{stamp}-{digest}.zip"
    zip_path = OUT / name
    if zip_path.exists():
        zip_path.unlink()
    packed_names: list[str] = []
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(WORK.rglob("*")):
            if not path.is_file() or path.name == ".keep":
                continue
            # Never ship Python sources in the datapack
            if path.suffix.lower() == ".py":
                continue
            arc = _arcname_for(path, stamp)
            zf.write(path, arcname=arc)
            packed_names.append(arc)
        manifest = {
            "brand": "RootRecord",
            "created_at": now_hst().isoformat(),
            "pack_id": f"{stamp}-{digest}",
            "sha256_12": digest,
            "naming": "timestamp_prefix_all_non_py",
            "files": packed_names,
        }
        zf.writestr("manifest.json", json.dumps(manifest, indent=2) + "\n")
    size = zip_path.stat().st_size
    meta = {
        "pack_id": manifest["pack_id"],
        "path": str(zip_path),
        "bytes": size,
        "sha256_12": digest,
        "created_at": manifest["created_at"],
        "file_count": len(packed_names),
    }
    write_json(OUT / "last-pack-meta.json", meta)
    return zip_path, meta


def split_if_needed(zip_path: Path) -> list[Path]:
    size = zip_path.stat().st_size
    if size <= MAX_BYTES:
        return [zip_path]
    # Extremely unlikely; split into ~1.5GB parts
    part_size = int(1.5 * 1024 * 1024 * 1024)
    parts: list[Path] = []
    data = zip_path.read_bytes()
    for i in range(0, len(data), part_size):
        part = zip_path.with_name(zip_path.stem + f"-part{i // part_size + 1}.zip")
        part.write_bytes(data[i : i + part_size])
        parts.append(part)
    manifest = {
        "multipart": True,
        "parts": [p.name for p in parts],
        "total_bytes": size,
        "original": zip_path.name,
    }
    man_path = zip_path.with_name(zip_path.stem + "-manifest.json")
    man_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    parts.insert(0, man_path)
    return parts


def send_telegram(paths: list[Path]) -> None:
    token = _token()
    chat = _channel()
    if not token:
        raise RuntimeError("RR_DATAPACK_SEND_BOT_TOKEN (or RR_DATAPACK_BOT_TOKEN) required")
    if not chat:
        raise RuntimeError("RR_DATAPACK_CHAT_ID required")
    # Groups: BotFather privacy mode hides other bots' posts unless @mentioned.
    mention = (os.environ.get("RR_DATAPACK_RECV_MENTION") or "@rootreceiver_bot").strip()
    with httpx.Client(timeout=300.0) as client:
        for path in paths:
            caption = f"RootRecord datapack {path.name} {mention}".strip()
            with path.open("rb") as f:
                r = client.post(
                    f"{API}/bot{token}/sendDocument",
                    data={"chat_id": chat, "caption": caption},
                    files={"document": (path.name, f)},
                )
            if r.status_code != 200 or not r.json().get("ok"):
                raise RuntimeError(f"telegram send failed for {path.name}: {r.text[:300]}")
            log.info("sent %s bytes=%s", path.name, path.stat().st_size)


def wipe_work() -> None:
    ensure_dirs()
    for child in WORK.iterdir():
        if child.is_dir():
            shutil.rmtree(child)
            child.mkdir(parents=True, exist_ok=True)
            (child / ".keep").touch()
        elif child.is_file():
            child.unlink()
    # clear out zips after send
    for path in OUT.glob("rootrecord-*.zip"):
        try:
            path.unlink()
        except OSError:
            pass
    for path in OUT.glob("rootrecord-*-manifest.json"):
        try:
            path.unlink()
        except OSError:
            pass
    if WIPE_LOCK.exists():
        WIPE_LOCK.unlink()
    write_json(ETC / "last-wipe.json", {"ok": True, "at": now_hst().isoformat()})


def restart_stack() -> None:
    """Absorb drop-in / config edits without manual commands. Skips killing packer mid-flight."""
    script = ROOT / "bin" / "restart_all.sh"
    if not script.is_file():
        return
    env = os.environ.copy()
    env["RR_SKIP_PACKER"] = "1"
    try:
        subprocess.run(
            ["sudo", "-n", "/bin/bash", str(script)],
            env=env,
            timeout=120,
            check=False,
        )
        log.info("stack restart requested")
    except Exception as exc:
        log.warning("stack restart failed: %s", exc)
    # Bounce packer itself shortly after we exit this tick (via systemd)
    try:
        subprocess.Popen(
            ["sudo", "-n", "systemctl", "restart", "rr-packer.service"],
            start_new_session=True,
        )
    except Exception as exc:
        log.warning("packer self-restart schedule failed: %s", exc)


def pack_and_send() -> dict:
    if WIPE_LOCK.is_file():
        raise RuntimeError("previous wipe failed — refusing to pack until cleared")
    zip_path, meta = build_zip()
    log.info("built pack %s bytes=%s files=%s", meta["pack_id"], meta["bytes"], meta.get("file_count"))
    parts = split_if_needed(zip_path)
    try:
        send_telegram(parts)
    except Exception:
        raise
    try:
        wipe_work()
    except Exception as exc:
        WIPE_LOCK.write_text(str(exc)[:500], encoding="utf-8")
        raise
    try:
        import automation_kb
        from common import now_hst
        automation_kb.update_section(
            "packer",
            {"last_wipe_at": now_hst().isoformat(), "ok": True, "last_pack_id": meta.get("pack_id")},
        )
    except Exception:
        pass
    meta["sent"] = True
    meta["wiped"] = True
    write_json(LOGS / "packer-last.json", meta)
    # After every successful send: restart so FileZilla/drop-in edits load
    restart_stack()
    return meta


def seconds_until_next_pack() -> float:
    now = now_hst()
    minute = now.minute
    second = now.second + now.microsecond / 1e6
    # next pack minute in PACK_MINUTES
    candidates = sorted(PACK_MINUTES)
    for m in candidates:
        if m > minute or (m == minute and second < 5):
            target_m = m
            break
    else:
        target_m = candidates[0] + 60
    delta_m = target_m - minute
    if delta_m < 0:
        delta_m += 60
    return max(1.0, delta_m * 60 - second)


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    load_dotenv()
    log.info("RootRecord packer start (HST slots %s)", sorted(PACK_MINUTES))
    # Optional one-shot
    if "--once" in sys.argv:
        print(json.dumps(pack_and_send(), indent=2))
        return
    while True:
        wait = seconds_until_next_pack()
        log.info("sleep %.0fs until next pack slot", wait)
        time.sleep(wait)
        try:
            meta = pack_and_send()
            log.info("pack ok id=%s", meta.get("pack_id"))
        except Exception as exc:
            log.warning("pack failed: %s", exc)
            write_json(
                LOGS / "packer-last.json",
                {"ok": False, "detail": str(exc)[:400], "at": now_hst().isoformat()},
            )
        time.sleep(30)  # avoid double-fire in same minute


if __name__ == "__main__":
    main()
