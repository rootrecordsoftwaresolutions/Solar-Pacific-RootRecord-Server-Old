"""Ask AWS to post the solar-cam GIF/still (media stays on rr-aws)."""
from __future__ import annotations

import re
import subprocess
from typing import Any

_SOLAR_ASK = re.compile(
    r"(?:"
    r"\b(?:show|see|look\s+at|check)\b.{0,48}\b(?:solar|panels?|cameras?|cams?|night\s*owl)\b"
    r"|"
    r"\b(?:solar|panels?)\s+(?:cam|camera|gif|loop|view)\b"
    r"|"
    r"\bshow\s+me\s+solar\b"
    r")",
    re.I | re.S,
)


def is_solar_ask(text: str) -> bool:
    return bool(_SOLAR_ASK.search(text or ""))


def request_aws_post(
    *,
    chat_id: int | str,
    reply_to: int | None = None,
    force: bool = False,
    still: bool = False,
) -> dict[str, Any]:
    """SSH to rr-aws solar_post_once.py — never sends media bytes from OmniBook."""
    args = [
        "/home/ubuntu/rootrecord/venv/bin/python",
        "/home/ubuntu/rootrecord/bin/solar_post_once.py",
        f"--chat-id {int(chat_id)}",
    ]
    if reply_to:
        args.append(f"--reply-to {int(reply_to)}")
    if force:
        args.append("--force")
    if still:
        args.append("--still")
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
