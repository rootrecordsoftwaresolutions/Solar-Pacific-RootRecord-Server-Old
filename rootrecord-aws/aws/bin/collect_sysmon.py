#!/usr/bin/env python3
"""Collect logs + system monitoring into work/sysmon for the next datapack zip."""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import LOGS, ROOT, WORK, ensure_dirs, now_hst, write_json


def _run(cmd: list[str], timeout: float = 15.0) -> str:
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return (p.stdout or "") + (p.stderr or "")
    except Exception as exc:
        return f"error: {exc}\n"


def collect() -> dict:
    ensure_dirs()
    dest = WORK / "sysmon"
    dest.mkdir(parents=True, exist_ok=True)
    # Copy recent process logs (exclude secrets)
    log_dest = dest / "logs"
    if log_dest.exists():
        shutil.rmtree(log_dest)
    log_dest.mkdir(parents=True)
    copied = []
    if LOGS.is_dir():
        for path in sorted(LOGS.glob("*.log")):
            try:
                text = path.read_text(encoding="utf-8", errors="replace")
                # scrub tokens if any slipped in
                import re

                text = re.sub(r"\d{8,}:[A-Za-z0-9_-]{20,}", "<redacted-token>", text)
                # keep last 200KB per log
                if len(text) > 200_000:
                    text = text[-200_000:]
                (log_dest / path.name).write_text(text, encoding="utf-8")
                copied.append(path.name)
            except OSError:
                pass

    snap = {
        "updated_at": now_hst().isoformat(),
        "hostname": _run(["hostname"]).strip(),
        "uptime": _run(["uptime"]).strip(),
        "free": _run(["free", "-h"]),
        "df": _run(["df", "-h"]),
        "systemctl": _run(
            [
                "systemctl",
                "is-active",
                "rr-weather",
                "rr-earthquake",
                "rr-radar",
                "rr-hurricane",
                "rr-noaa",
                "rr-packer",
                "rr-radio",
                "rr-icecast",
                "rr-youtube",
                "rr-dropins",
                "rr-cloudflared",
            ]
        ),
        "systemctl_status": _run(
            ["bash", "-lc", "systemctl status rr-weather rr-packer rr-radio rr-icecast --no-pager -l | head -80"]
        ),
        "ps": _run(["bash", "-lc", "ps aux --sort=-%mem | head -25"]),
        "logs_copied": copied,
    }
    # Public radio URL (cloudflared) if present
    import re

    url_file = ROOT / "etc" / "radio-public.url"
    radio_url = ""
    if url_file.is_file():
        radio_url = url_file.read_text(encoding="utf-8", errors="replace").strip()
    if not radio_url:
        cf_log = LOGS / "cloudflared.log"
        if cf_log.is_file():
            text = cf_log.read_text(encoding="utf-8", errors="replace")
            found = re.findall(r"https://[a-zA-Z0-9.-]+\.trycloudflare\.com", text)
            if found:
                radio_url = found[-1]
                url_file.write_text(radio_url + "\n", encoding="utf-8")
    if radio_url:
        (dest / "radio-public.url").write_text(radio_url + "\n", encoding="utf-8")
    snap["radio_public_url"] = radio_url

    write_json(dest / "Current.json", snap)
    (dest / "Current.txt").write_text(
        "\n".join(
            [
                f"RootRecord sysmon {snap['updated_at']}",
                snap["hostname"],
                snap["uptime"],
                f"radio_public_url={radio_url or '(none yet)'}",
                "",
                snap["free"],
                snap["df"],
                snap["systemctl"],
            ]
        ),
        encoding="utf-8",
    )
    return {"ok": True, "logs": len(copied), "updated_at": snap["updated_at"]}


if __name__ == "__main__":
    print(json.dumps(collect(), indent=2))
