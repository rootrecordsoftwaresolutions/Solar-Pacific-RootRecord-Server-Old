"""
Local clip engine — concatenates pre-recorded clips via ffmpeg.
Canonical output: WAV 44.1 kHz 16-bit PCM. Prefers .wav stems over .mp3.
No API calls. Used for deterministic content (numbers, time, status phrases).
"""

from __future__ import annotations

import logging
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

log = logging.getLogger("ava.clips")

CREATE_NO_WINDOW = 0x08000000


def _run(cmd: list[str], **kwargs):
    if os.name == "nt":
        kwargs.setdefault("creationflags", CREATE_NO_WINDOW)
    return subprocess.run(cmd, **kwargs)

# Clips live in the central media library (media/audio), not apps/voice/assets.
from apps.core import config as _cfg
ASSETS_DIR  = _cfg.ASSETS_DIR
NUMBERS_DIR = ASSETS_DIR / "numbers"
WORDS_DIR   = ASSETS_DIR / "words"
TIME_DIR    = ASSETS_DIR / "time_clips"
SOUNDS_DIR  = ASSETS_DIR / "sounds"
PHONEME_DIR = ASSETS_DIR / "phonemes"

SILENCE_MS  = 50  # gap between clips in ms (tight radio blend; was 90)


def ffmpeg_bin() -> str | None:
    from_env = (os.getenv("AVA_FFMPEG") or "").strip()
    if from_env and Path(from_env).is_file():
        return from_env
    found = shutil.which("ffmpeg")
    if found:
        return found
    local = os.environ.get("LOCALAPPDATA", "")
    if local:
        root = Path(local) / "Microsoft" / "WinGet" / "Packages"
        if root.is_dir():
            for p in root.glob("Gyan.FFmpeg*/ffmpeg-*/bin/ffmpeg.exe"):
                if p.is_file():
                    return str(p)
    try:
        import imageio_ffmpeg

        exe = imageio_ffmpeg.get_ffmpeg_exe()
        if exe and Path(exe).is_file():
            return exe
    except Exception:
        pass
    return None


def _number_to_clips(n: int) -> list[str]:
    """
    Decompose integer n into clip filenames.
    Prefers a direct clip file (e.g. '1000000.wav') over decomposing into
    sub-words, so large number clips recorded by Ara play naturally.
    """
    if n < 0:
        return ["negative"] + _number_to_clips(-n)
    if n == 0:
        return ["0"]

    # If there's a direct clip for this exact number, use it
    if (NUMBERS_DIR / f"{n}.wav").exists() or (NUMBERS_DIR / f"{n}.mp3").exists():
        return [str(n)]

    clips: list[str] = []
    if n >= 1_000_000_000:
        clips += _number_to_clips(n // 1_000_000_000) + ["billion"]
        n %= 1_000_000_000
        if n:
            clips += _number_to_clips(n)
        return clips
    if n >= 1_000_000:
        clips += _number_to_clips(n // 1_000_000) + ["million"]
        n %= 1_000_000
        if n:
            clips += _number_to_clips(n)
        return clips
    if n >= 1_000:
        clips += _number_to_clips(n // 1_000) + ["thousand"]
        n %= 1_000
        if n:
            clips += _number_to_clips(n)
        return clips
    if n >= 100:
        clips += _number_to_clips(n // 100) + ["hundred"]
        n %= 100
        if n:
            clips += ["and"] + _number_to_clips(n)
        return clips
    clips.append(str(n))
    return clips


def _find_clip(name: str) -> Path | None:
    """Search numbers/ first so 1.wav is the digit, not a word collision.

    Prefers .wav over .mp3 when both exist (canonical desk format).
    Also checks words/nws/, words/ecoflow/, and words/hurricane/ after flat words/
    so existing weather/number clips keep priority and each pack stays a subtree.
    """
    search_dirs = (
        NUMBERS_DIR,
        WORDS_DIR,
        WORDS_DIR / "nws",
        WORDS_DIR / "ecoflow",
        WORDS_DIR / "hurricane",
        TIME_DIR,
        SOUNDS_DIR,
        PHONEME_DIR,
        ASSETS_DIR,
    )
    for directory in search_dirs:
        for ext in (".wav", ".mp3"):
            p = directory / (name + ext)
            if p.exists():
                return p
    return None


def _escape_concat_path(path: Path) -> str:
    return str(path.resolve()).replace("'", r"'\''")


def concatenate_clips(
    clips: list[Path],
    out_path: Path,
    *,
    silence_ms: int | None = None,
) -> Path:
    """
    Concatenate clips into one WAV (44.1 kHz 16-bit PCM mono).

    Must re-encode. `-c copy` fails across mixed containers (wav/mp3).
    Every input is normalized to mono 44.1k first — mixing zip mono Ara
    stems with stereo converts made numbers play in slow-mo.

    Skip inserted silence next to pause clips (comma_pause / period_pause /
    section_pause) — those files are already open space.

    silence_ms: override SILENCE_MS (use 0 for dense NWS/radio packs).
    """
    if not clips:
        raise ValueError("No clips to concatenate")

    out_path = Path(out_path)
    if out_path.suffix.lower() != ".wav":
        out_path = out_path.with_suffix(".wav")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    gap_ms = SILENCE_MS if silence_ms is None else max(0, int(silence_ms))
    gap = gap_ms / 1000.0
    pause_names = {"comma_pause", "period_pause", "section_pause"}

    def _is_pause(p: Path) -> bool:
        return p.stem.lower() in pause_names

    ff = ffmpeg_bin()
    if not ff:
        raise FileNotFoundError("ffmpeg missing")

    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = Path(tmp)
        list_file = tmpdir / "concat.txt"
        need_silence = False
        norm_paths: list[Path] = []

        def _normalize(src: Path, dest: Path) -> None:
            result = _run(
                [
                    ff, "-y",
                    "-i", str(Path(src).resolve()),
                    "-acodec", "pcm_s16le",
                    "-ar", "44100",
                    "-ac", "1",
                    str(dest),
                ],
                capture_output=True,
                text=True,
            )
            if result.returncode != 0 or not dest.is_file():
                raise RuntimeError(
                    f"ffmpeg normalize failed for {src.name}:\n{result.stderr}"
                )

        for i, clip in enumerate(clips):
            norm = tmpdir / f"n{i:04d}.wav"
            _normalize(Path(clip), norm)
            norm_paths.append(norm)

        with open(list_file, "w", encoding="utf-8") as f:
            for i, norm in enumerate(norm_paths):
                f.write(f"file '{_escape_concat_path(norm)}'\n")
                if i >= len(norm_paths) - 1 or gap_ms <= 0:
                    continue
                a = Path(clips[i])
                b = Path(clips[i + 1])
                if _is_pause(a) or _is_pause(b):
                    continue
                f.write(f"file '{_escape_concat_path(tmpdir / 'silence.wav')}'\n")
                need_silence = True

        if need_silence:
            silence = tmpdir / "silence.wav"
            _run(
                [
                    ff, "-y",
                    "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
                    "-t", f"{gap:.3f}",
                    "-c:a", "pcm_s16le",
                    str(silence),
                ],
                capture_output=True,
                check=True,
            )

        result = _run(
            [
                ff, "-y",
                "-f", "concat", "-safe", "0",
                "-i", str(list_file),
                "-c:a", "pcm_s16le",
                "-ar", "44100",
                "-ac", "1",
                str(out_path),
            ],
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            raise RuntimeError(f"ffmpeg concat failed:\n{result.stderr}")
    return out_path


_DURATION_RE = re.compile(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)")


def mp3_duration_s(path: Path) -> float | None:
    """Seconds from ffmpeg -i. None if ffmpeg missing or no Duration line."""
    ff = ffmpeg_bin()
    if not ff or not path or not Path(path).is_file():
        return None
    result = _run(
        [ff, "-i", str(path)],
        capture_output=True,
        text=True,
    )
    blob = (result.stderr or "") + (result.stdout or "")
    m = _DURATION_RE.search(blob)
    if not m:
        return None
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))


def speak_text(text: str, out_path: Path) -> Path | None:
    """Kokoro WAV. Clip stitch is disabled."""
    from apps.voice.kokoro_tts import generate_wav

    dest = Path(out_path)
    if dest.suffix.lower() != ".wav":
        dest = dest.with_suffix(".wav")
    built = generate_wav(text, dest)
    return dest if built.get("ok") else None


def speak_number(n: int, out_path: Path) -> Path | None:
    """Kokoro WAV for one integer."""
    from apps.voice.kokoro_tts import generate_wav

    dest = Path(out_path)
    if dest.suffix.lower() != ".wav":
        dest = dest.with_suffix(".wav")
    built = generate_wav(str(int(n)), dest)
    return dest if built.get("ok") else None


def speak_time(hour: int, minute: int, out_path: Path) -> Path | None:
    """Kokoro spoken clock → WAV. Time clip files are not used."""
    from apps.voice.local_tts import spoken_clock
    from apps.voice.kokoro_tts import generate_wav

    dest = Path(out_path)
    if dest.suffix.lower() != ".wav":
        dest = dest.with_suffix(".wav")
    built = generate_wav(f"It's {spoken_clock(hour, minute)}.", dest)
    return dest if built.get("ok") else None


if __name__ == "__main__":
    import argparse
    import json
    import sys

    p = argparse.ArgumentParser(description="Kokoro number → WAV")
    p.add_argument("--number", required=True, help="Integer only")
    p.add_argument("--out", required=True)
    args = p.parse_args()
    raw = str(args.number).strip()
    if not re.fullmatch(r"-?\d+", raw):
        print(json.dumps({"ok": False, "reason": "numbers_only"}))
        sys.exit(1)
    dest = Path(args.out)
    got = speak_number(int(raw, 10), dest)
    if not got or not got.exists():
        print(json.dumps({"ok": False, "reason": "kokoro_failed"}))
        sys.exit(1)
    print(json.dumps({"ok": True, "wav": str(got.resolve())}))
    sys.exit(0)
