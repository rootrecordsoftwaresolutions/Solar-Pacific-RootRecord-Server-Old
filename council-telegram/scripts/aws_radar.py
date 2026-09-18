"""Ask AWS to post the radar GIF / weather brief (media stays on rr-aws)."""
from __future__ import annotations

import re
import subprocess
from typing import Any

_RADAR_ASK = re.compile(
    r"(?:"
    r"\bradar\b"
    r"|"
    r"\b(?:weather|nws|ridge|hawaii|hawai[`'ʻ]?i)\s+radar\b"
    r"|"
    r"\bradar\s+(?:gif|loop|image|images)\b"
    r")",
    re.I,
)


def is_radar_ask(text: str) -> bool:
    return bool(_RADAR_ASK.search(text or ""))


def request_aws_post(
    *,
    chat_id: int | str,
    reply_to: int | None = None,
    force: bool = False,
    gif_only: bool = False,
    weather_only: bool = False,
) -> dict[str, Any]:
    """SSH to rr-aws radar_post_once.py — never sends GIF bytes from OmniBook."""
    args = [
        "/home/ubuntu/rootrecord/venv/bin/python",
        "/home/ubuntu/rootrecord/bin/radar_post_once.py",
        f"--chat-id {int(chat_id)}",
    ]
    if reply_to:
        args.append(f"--reply-to {int(reply_to)}")
    if force:
        args.append("--force")
    if gif_only:
        args.append("--gif-only")
    if weather_only:
        args.append("--weather-only")
    remote = " ".join(args)
    cmd = [
        "ssh",
        "-o",
        "BatchMode=yes",
        "-o",
        "ConnectTimeout=12",
        "rr-aws",
        remote,
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=90)
    except Exception as exc:
        return {"ok": False, "error": str(exc)[:200]}
    out = (proc.stdout or "").strip()
    err = (proc.stderr or "").strip()
    return {
        "ok": proc.returncode == 0 and out in {"ok", "cooldown"},
        "stdout": out[:200],
        "stderr": err[:200],
        "code": proc.returncode,
    }
