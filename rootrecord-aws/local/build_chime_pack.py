#!/usr/bin/env python3
"""Build 48 RootRecord radio time-chime WAVs for AWS (:00 and :30 × 24h).

Rotates Ava → Bruce → Carly across the marks. Each clip is bell + spoken clock.
Output: rootrecord-aws/radio/chimes/chime-HHMM.wav + manifest.json
"""
from __future__ import annotations

import json
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "radio" / "chimes"
KOKORO = Path.home() / ".ollama" / "skills" / "kokoro"
KOKORO_PY = KOKORO / "store" / "venv" / "bin" / "python"
GEN = KOKORO / "scripts" / "generate.py"
SPEAKABLE = KOKORO / "scripts"
BELLS = [
    Path.home() / "Media" / "public" / "audio" / "sounds" / "futuristic_bell.mp3",
    Path.home() / "Media" / "public" / "audio" / "voice" / "sounds" / "futuristic_bell.mp3",
    Path.home() / "Media" / "public" / "audio" / "generated" / "futuristic_bell.wav",
]
AGENTS = ("ava", "bruce", "carly")
HST = ZoneInfo("Pacific/Honolulu")


def slots() -> list[tuple[int, int]]:
    return [(h, m) for h in range(24) for m in (0, 30)]


def spoken_line(hour: int, minute: int) -> str:
    sys.path.insert(0, str(SPEAKABLE))
    from speakable import spoken_clock  # type: ignore

    return f"It's {spoken_clock(hour, minute)} Hawaiian Standard Time."


def find_bell() -> Path | None:
    for p in BELLS:
        if p.is_file():
            return p
    return None


def concat_bell_voice(bell: Path | None, voice: Path, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if bell is None:
        dest.write_bytes(voice.read_bytes())
        return
    # Normalize to 24k mono wav via ffmpeg, then concat PCM
    tmp_dir = dest.parent / ".tmp"
    tmp_dir.mkdir(parents=True, exist_ok=True)
    bell_wav = tmp_dir / f"{dest.stem}-bell.wav"
    cmd = [
        "ffmpeg",
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        str(bell),
        "-i",
        str(voice),
        "-filter_complex",
        "[0:a]aformat=sample_rates=24000:channel_layouts=mono[b];"
        "[1:a]aformat=sample_rates=24000:channel_layouts=mono[v];"
        "[b][v]concat=n=2:v=0:a=1[a]",
        "-map",
        "[a]",
        str(dest),
    ]
    r = subprocess.run(cmd, check=False, capture_output=True, text=True)
    if r.returncode != 0 or not dest.is_file():
        # Fallback: voice only
        dest.write_bytes(voice.read_bytes())
        if r.stderr:
            print(f"concat warn {dest.name}: {r.stderr[:200]}", file=sys.stderr)
    try:
        bell_wav.unlink(missing_ok=True)
    except OSError:
        pass


def synthesize(text: str, voice: str, out: Path) -> dict:
    out.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        str(KOKORO_PY),
        str(GEN),
        "--voice",
        voice,
        "--out",
        str(out),
        "--text",
        text,
    ]
    r = subprocess.run(cmd, check=False, capture_output=True, text=True)
    if r.returncode != 0:
        return {"ok": False, "detail": (r.stderr or r.stdout)[:400]}
    try:
        return json.loads(r.stdout.strip().splitlines()[-1])
    except (json.JSONDecodeError, IndexError):
        return {"ok": out.is_file(), "raw": r.stdout[-200:]}


def main() -> int:
    if not KOKORO_PY.is_file():
        print(json.dumps({"ok": False, "detail": f"missing {KOKORO_PY}"}))
        return 1
    bell = find_bell()
    OUT.mkdir(parents=True, exist_ok=True)
    work = OUT / ".voice"
    work.mkdir(parents=True, exist_ok=True)
    manifest = {
        "ok": True,
        "built_at": datetime.now(HST).isoformat(),
        "count": 48,
        "rotation": list(AGENTS),
        "bell": str(bell) if bell else None,
        "clips": [],
    }
    ok_n = 0
    for i, (hour, minute) in enumerate(slots()):
        agent = AGENTS[i % 3]
        mark = f"{hour:02d}{minute:02d}"
        dest = OUT / f"chime-{mark}.wav"
        voice_path = work / f"chime-{mark}-{agent}.wav"
        text = spoken_line(hour, minute)
        print(f"[{i+1}/48] {mark} {agent}: {text}", flush=True)
        built = synthesize(text, agent, voice_path)
        if not built.get("ok"):
            print(json.dumps({"ok": False, "mark": mark, "detail": built}))
            return 1
        concat_bell_voice(bell, voice_path, dest)
        entry = {
            "mark": mark,
            "hour": hour,
            "minute": minute,
            "agent": agent,
            "file": dest.name,
            "bytes": dest.stat().st_size if dest.is_file() else 0,
            "script": text,
        }
        manifest["clips"].append(entry)
        ok_n += 1
        # Side text for operators
        (OUT / f"chime-{mark}.speak.txt").write_text(text + "\n", encoding="utf-8")
        (OUT / f"chime-{mark}.agent.txt").write_text(agent + "\n", encoding="utf-8")

    manifest["ok"] = ok_n == 48
    manifest["built"] = ok_n
    (OUT / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({"ok": ok_n == 48, "built": ok_n, "out": str(OUT)}, indent=2))
    return 0 if ok_n == 48 else 1


if __name__ == "__main__":
    raise SystemExit(main())
