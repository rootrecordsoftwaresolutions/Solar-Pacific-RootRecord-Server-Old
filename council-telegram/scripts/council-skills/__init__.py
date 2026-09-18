"""Load allowlisted council skills (markdown recipes + optional gated run.py)."""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

from .hawaiian_dict import lookup_in_text
from .hawaiian_inject import format_hawaiian_block
from .hawaiian_norm import fold_hawaiian

SKILLS_DIR = Path(__file__).resolve().parent
ORIGIN_SKILLS = Path.home() / ".ollama" / "skills" / "origin" / "ns" / "apps" / "council" / "skills"
CATALOG_PATH = SKILLS_DIR / "catalog.json"
if not CATALOG_PATH.is_file():
    CATALOG_PATH = ORIGIN_SKILLS / "catalog.json"
STDOUT_CAP = 8 * 1024
EXEC_TIMEOUT_S = 20
READ_SKILL_CAP = 1000
DISK_SESSION_FLAGS = ("--status", "--prepare", "--on", "--off", "--execute", "--session", "--hold")
MAX_EXEC_TIMEOUT_S = 90

# “show me the camera(s) / solar panels / night owl” — not only exact keyword strings.
_PANELS_CAM_ASK = re.compile(
    r"(?:"
    r"\b(?:show|see|look(?:\s+at)?|check|grab|pull|get)\b.{0,48}\b"
    r"(?:panels?|solar\s+panels?|cameras?|cams?|night\s*owl|rear\s+shed|shed\s+cam|site\s+cam)\b"
    r"|"
    r"\b(?:panels?|solar\s+panels?|rear\s+shed|night\s*owl|shed|site)\b.{0,40}\b"
    r"(?:cam|cams|camera|cameras|still|picture|photo)\b"
    r"|"
    r"\b(?:panel|panels|shed|site)\s+cameras?\b"
    r")",
    re.I | re.S,
)

# “radar / weather radar / Hawaiʻi radar gif” — natural asks, not only exact catalog strings.
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

def _load_catalog() -> dict[str, Any]:
    try:
        data = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        data = {"skills": []}
    return data


def list_skills() -> list[dict[str, Any]]:
    skills = _load_catalog().get("skills") or []
    return [s for s in skills if isinstance(s, dict) and s.get("id")]


def get_skill(skill_id: str) -> dict[str, Any] | None:
    sid = (skill_id or "").strip().lower()
    for s in list_skills():
        if str(s.get("id") or "").lower() == sid:
            return s
    return None


def skill_dir(skill_id: str) -> Path:
    sid = str(skill_id).strip()
    local = SKILLS_DIR / sid
    origin = ORIGIN_SKILLS / sid
    if (local / "run.py").is_file():
        return local
    if origin.is_dir():
        return origin
    return local


def read_skill_md(skill_id: str, cap: int = READ_SKILL_CAP) -> str:
    path = skill_dir(skill_id) / "SKILL.md"
    if not path.is_file():
        return ""
    text = path.read_text(encoding="utf-8", errors="replace").strip()
    if len(text) > cap:
        text = text[: cap - 1] + "…"
    return text


def match_read_skills(text: str, *, voice: str | None = None, limit: int = 3) -> list[str]:
    folded = fold_hawaiian(text or "")
    if not folded:
        return []
    hits: list[str] = []
    for s in list_skills():
        if str(s.get("risk") or "read") != "read":
            continue
        voices = [str(v).lower() for v in (s.get("voices") or [])]
        if voice and voices and voice.lower() not in voices:
            continue
        kws = [fold_hawaiian(str(k)) for k in (s.get("keywords") or [])]
        if any(k and k in folded for k in kws):
            sid = str(s.get("id"))
            if sid not in hits:
                hits.append(sid)
        if len(hits) >= limit:
            break
    if "hawaiian-glossary" not in hits and lookup_in_text(text or ""):
        if not voice or voice.lower() in {"ava", "bruce", "carly"}:
            if len(hits) >= limit:
                for i in range(len(hits) - 1, -1, -1):
                    if hits[i] != "kilauea-glossary":
                        hits[i] = "hawaiian-glossary"
                        break
            else:
                hits.append("hawaiian-glossary")
    return hits


def disk_session_argv(text: str, *, execute: bool = False) -> list[str]:
    """Owner execute=True PUTs River car DC. Anyone else is dry-run. Status if no on/off."""
    low = (text or "").lower()
    off = any(
        p in low
        for p in (
            "turn off the drive",
            "turn off the disk",
            "power off the drive",
            "drives off",
            "drive power off",
            "car dc off",
        )
    )
    on = any(
        p in low
        for p in (
            "turn on the drive",
            "turn on the disk",
            "power on the drive",
            "power the drive",
            "spin up",
            "prepare the disk",
            "need the drive",
            "need the disk",
            "car dc on",
            "drive session",
        )
    )
    if off:
        flags = ["--off"]
    elif "session" in low and "drive" in low:
        flags = ["--session"]
    elif on:
        flags = ["--prepare"]
    else:
        return []
    if execute:
        flags.append("--execute")
    return flags


def sanitize_disk_argv(extra: list[str] | None) -> list[str]:
    out: list[str] = []
    for a in extra or []:
        if str(a) in DISK_SESSION_FLAGS and str(a) not in out:
            out.append(str(a))
    if "--on" in out and "--prepare" not in out:
        out = ["--prepare" if a == "--on" else a for a in out]
    return out


def match_exec_skill(text: str) -> dict[str, Any] | None:
    low = (text or "").lower()
    for s in list_skills():
        if str(s.get("risk") or "") != "exec":
            continue
        kws = [str(k).lower() for k in (s.get("keywords") or [])]
        if any(k and k in low for k in kws):
            return s
    # Flexible panels / site camera asks (keyword list alone misses “solar panels”, “cameras”).
    if _PANELS_CAM_ASK.search(text or ""):
        hit = get_skill("panels-cam")
        if hit and str(hit.get("risk") or "") == "exec":
            return hit
    if _RADAR_ASK.search(text or ""):
        hit = get_skill("radar-gif")
        if hit and str(hit.get("risk") or "") == "exec":
            return hit
    return None


def inject_blocks(skill_ids: list[str], text: str = "") -> str:
    chunks: list[str] = []
    for sid in skill_ids:
        if sid == "hawaiian-glossary":
            body = format_hawaiian_block(text)
        else:
            body = read_skill_md(sid)
        if body:
            chunks.append(f"Skill {sid} (facts only; do not invent beyond this):\n{body}")
    return "\n\n".join(chunks)


def run_exec(skill_id: str, extra_argv: list[str] | None = None) -> dict[str, Any]:
    """Allowlisted run.py only. No shell, no secrets, network off by default."""
    meta = get_skill(skill_id)
    if not meta or str(meta.get("risk") or "") != "exec":
        return {"ok": False, "text": "skill not executable"}
    entry = str(meta.get("entrypoint") or "run.py")
    if entry != "run.py" or "/" in entry or "\\" in entry or ".." in entry:
        return {"ok": False, "text": "bad entrypoint"}
    cwd = skill_dir(skill_id)
    script = cwd / entry
    if not script.is_file():
        return {"ok": False, "text": "missing run.py"}
    argv = meta.get("argv") if isinstance(meta.get("argv"), list) else []
    extra = extra_argv if isinstance(extra_argv, list) else []
    safe_argv = [
        str(a)
        for a in [*argv, *extra]
        if isinstance(a, (str, int)) and ".." not in str(a)
    ]
    env = {
        "PATH": os.environ.get("PATH", "/usr/bin:/bin"),
        "LANG": os.environ.get("LANG", "C.UTF-8"),
        "LC_ALL": os.environ.get("LC_ALL") or os.environ.get("LANG") or "C.UTF-8",
        "PYTHONPATH": str(SKILLS_DIR.parents[2]),
        "PYTHONDONTWRITEBYTECODE": "1",
    }
    home = os.environ.get("HOME")
    if home:
        env["HOME"] = home
    user = os.environ.get("USER")
    if user:
        env["USER"] = user
    # network=false: do not pass proxy/tokens; caller must not inherit secrets.env
    # disk-session loads EcoFlow keys from Ava-Core .env itself (mpptCar only).
    try:
        timeout_s = int(meta.get("timeout") or EXEC_TIMEOUT_S)
    except (TypeError, ValueError):
        timeout_s = EXEC_TIMEOUT_S
    timeout_s = min(max(timeout_s, 5), MAX_EXEC_TIMEOUT_S)
    cmd = [sys.executable, str(script), *[str(a) for a in safe_argv]]
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(cwd),
            env=env,
            capture_output=True,
            timeout=timeout_s,
            check=False,
            shell=False,
        )
    except subprocess.TimeoutExpired:
        return {"ok": False, "text": "skill timed out"}
    except OSError as e:
        return {"ok": False, "text": f"skill failed ({type(e).__name__})"}
    out = (proc.stdout or b"").decode("utf-8", errors="replace")
    err = (proc.stderr or b"").decode("utf-8", errors="replace")
    blob = out.strip() or err.strip() or f"exit {proc.returncode}"
    if len(blob) > STDOUT_CAP:
        blob = blob[: STDOUT_CAP - 1] + "…"
    low = blob.lower()
    if skill_id != "desk-read" and (
        "token=" in low or "api_key=" in low or "begin private" in low
    ):
        return {"ok": False, "text": "skill output blocked (looked like a secret)"}
    return {"ok": proc.returncode == 0, "text": blob, "code": proc.returncode}
