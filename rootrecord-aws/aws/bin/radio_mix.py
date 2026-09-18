#!/usr/bin/env python3
"""Always-on Icecast mixer: loop music beds; duck to 50% under voice inserts.

Voice files land in work/audio/Current*.{wav,mp3} (from Telegram audio_recv).
Fresh reports (received within RR_REPORT_STALE_SEC, default 1.5h) ALWAYS cut over
music. If no new report arrives within that window, Current* files are discarded
unplayed and rotating offline fallbacks (Ava / Bruce / Carly) play instead.

After each insert, the file is archived so it plays once. Single continuous
ffmpeg→Icecast process; only mute is client-side.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(os.environ.get("RR_ROOT", "/home/ubuntu/rootrecord"))
MEDIA = ROOT / "radio" / "media"
AUDIO = ROOT / "work" / "audio"
FALLBACK = ROOT / "radio" / "fallback"
VOICE_DONE = ROOT / "radio" / "voice-played"
ETC = ROOT / "etc"
LOGS = ROOT / "logs"
PASS_FILE = ETC / "radio.source.password"
META = AUDIO / "Current.meta.json"
FALLBACK_STATE = AUDIO / "fallback-state.json"
ICE_HOST = os.environ.get("RR_ICE_HOST", "127.0.0.1")
ICE_PORT = os.environ.get("RR_ICE_PORT", "8000")
MOUNT = os.environ.get("RR_ICE_MOUNT", "/rootrecord.mp3")
MUSIC_VOL = float(os.environ.get("RR_RADIO_MUSIC_VOL", "1.0"))
DUCK_VOL = float(os.environ.get("RR_RADIO_DUCK_VOL", "0.5"))  # music under voice
VOICE_VOL = float(os.environ.get("RR_RADIO_VOICE_VOL", "1.0"))
# No new report within this many seconds → play offline fallbacks only
STALE_SEC = float(os.environ.get("RR_REPORT_STALE_SEC", str(int(1.5 * 3600))))
# Seconds between offline fallback inserts while stale
FALLBACK_EVERY_SEC = float(os.environ.get("RR_FALLBACK_EVERY_SEC", "900"))
AGENTS = ("ava", "bruce", "carly")


def log(msg: str) -> None:
    line = time.strftime("%Y-%m-%dT%H:%M:%S%z") + " " + msg
    print(line, flush=True)
    try:
        LOGS.mkdir(parents=True, exist_ok=True)
        with (LOGS / "radio.log").open("a", encoding="utf-8") as f:
            f.write(line + "\n")
    except OSError:
        pass


def ice_url() -> str:
    ETC.mkdir(parents=True, exist_ok=True)
    if not PASS_FILE.is_file():
        import secrets

        PASS_FILE.write_text(secrets.token_hex(12), encoding="utf-8")
        PASS_FILE.chmod(0o600)
    pw = PASS_FILE.read_text(encoding="utf-8").strip()
    return f"icecast://source:{pw}@{ICE_HOST}:{ICE_PORT}{MOUNT}"


def music_files() -> list[Path]:
    if not MEDIA.is_dir():
        return []
    files = sorted(
        [
            p
            for p in MEDIA.rglob("*")
            if p.is_file() and p.suffix.lower() in (".mp3", ".wav", ".ogg", ".m4a")
        ]
    )
    return files


def _list_current_audio() -> list[Path]:
    AUDIO.mkdir(parents=True, exist_ok=True)
    cands: list[Path] = []
    for pat in ("Current*.wav", "current*.wav", "Current*.mp3", "current*.mp3"):
        cands.extend(AUDIO.glob(pat))
    return [p for p in cands if p.is_file() and p.stat().st_size > 44]


def last_report_epoch() -> float | None:
    """Newest known report arrival time (meta JSON or Current* mtime)."""
    best: float | None = None
    if META.is_file():
        try:
            data = json.loads(META.read_text(encoding="utf-8"))
            epoch = data.get("received_epoch")
            if isinstance(epoch, (int, float)):
                best = float(epoch)
            else:
                ts = data.get("updated_at") or data.get("received_at")
                if isinstance(ts, (int, float)):
                    best = float(ts)
                elif isinstance(ts, str) and ts.strip():
                    from datetime import datetime

                    best = datetime.fromisoformat(ts).timestamp()
        except (OSError, json.JSONDecodeError, ValueError, TypeError):
            pass
    for p in _list_current_audio():
        try:
            m = p.stat().st_mtime
        except OSError:
            continue
        if best is None or m > best:
            best = m
    return best


def reports_fresh() -> bool:
    epoch = last_report_epoch()
    if epoch is None:
        return False
    age = time.time() - epoch
    return age <= STALE_SEC


def archive_voice(path: Path) -> None:
    VOICE_DONE.mkdir(parents=True, exist_ok=True)
    dest = VOICE_DONE / f"{int(time.time())}-{path.name}"
    try:
        shutil.move(str(path), str(dest))
    except OSError:
        try:
            path.unlink()
        except OSError:
            pass


def discard_stale_currents() -> int:
    """Archive Current* without playing when desk is offline / stale."""
    n = 0
    for p in _list_current_audio():
        log(f"discarding stale report (no play) {p.name}")
        archive_voice(p)
        n += 1
    return n


def pending_voice() -> Path | None:
    """Next live report insert, or None if stale / empty."""
    if not reports_fresh():
        discard_stale_currents()
        return None
    cands = _list_current_audio()
    if not cands:
        return None
    return sorted(cands, key=lambda p: p.stat().st_mtime)[0]


def _load_fallback_state() -> dict:
    if not FALLBACK_STATE.is_file():
        return {"next_idx": 0, "last_play": 0.0}
    try:
        data = json.loads(FALLBACK_STATE.read_text(encoding="utf-8"))
        return {
            "next_idx": int(data.get("next_idx") or 0),
            "last_play": float(data.get("last_play") or 0),
        }
    except (OSError, json.JSONDecodeError, TypeError, ValueError):
        return {"next_idx": 0, "last_play": 0.0}


def _save_fallback_state(state: dict) -> None:
    AUDIO.mkdir(parents=True, exist_ok=True)
    tmp = FALLBACK_STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps(state) + "\n", encoding="utf-8")
    tmp.replace(FALLBACK_STATE)


def fallback_files() -> list[Path]:
    if not FALLBACK.is_dir():
        return []
    out: list[Path] = []
    for agent in AGENTS:
        for ext in (".wav", ".mp3"):
            p = FALLBACK / f"{agent}-offline{ext}"
            if p.is_file() and p.stat().st_size > 44:
                out.append(p)
                break
    return out


def pending_fallback() -> Path | None:
    """When stale, rotate Ava→Bruce→Carly offline lines on an interval."""
    if reports_fresh():
        return None
    files = fallback_files()
    if not files:
        log("stale desk but no radio/fallback/*.wav — music only")
        return None
    state = _load_fallback_state()
    now = time.time()
    # First insert soon after going stale (30s grace), then every FALLBACK_EVERY_SEC
    gap = now - float(state["last_play"] or 0)
    if state["last_play"] and gap < FALLBACK_EVERY_SEC:
        return None
    if not state["last_play"] and gap < 30:
        # allow immediate on cold start / first stale detect
        pass
    idx = int(state["next_idx"]) % len(files)
    return files[idx]


def mark_fallback_played(path: Path) -> None:
    files = fallback_files()
    state = _load_fallback_state()
    try:
        idx = files.index(path)
        state["next_idx"] = (idx + 1) % max(len(files), 1)
    except ValueError:
        state["next_idx"] = (int(state["next_idx"]) + 1) % max(len(files), 1)
    state["last_play"] = time.time()
    _save_fallback_state(state)


def run_ffmpeg(args: list[str]) -> int:
    log("ffmpeg " + " ".join(args[1:8]) + " …")
    p = subprocess.run(args, check=False)
    return int(p.returncode or 0)


def stream_music_until_voice(url: str, beds: list[Path]) -> Path | None:
    """Loop music to Icecast; poll for voice/fallback. Returns path if found."""
    list_path = ROOT / "radio" / "Current-playlist.txt"
    list_path.parent.mkdir(parents=True, exist_ok=True)
    with list_path.open("w", encoding="utf-8") as f:
        for bed in beds:
            f.write(f"file '{bed}'\n")

    cmd = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "warning",
        "-re",
        "-f",
        "concat",
        "-safe",
        "0",
        "-stream_loop",
        "-1",
        "-i",
        str(list_path),
        "-filter:a",
        f"volume={MUSIC_VOL}",
        "-ac",
        "2",
        "-ar",
        "44100",
        "-c:a",
        "libmp3lame",
        "-b:a",
        "128k",
        "-content_type",
        "audio/mpeg",
        "-f",
        "mp3",
        url,
    ]
    proc = subprocess.Popen(cmd)
    try:
        while proc.poll() is None:
            voice = pending_voice()
            if voice is None:
                voice = pending_fallback()
            if voice is not None:
                kind = "fallback" if voice.parent == FALLBACK else "report"
                log(f"{kind} insert detected {voice.name} — ducking music")
                proc.terminate()
                try:
                    proc.wait(timeout=8)
                except subprocess.TimeoutExpired:
                    proc.kill()
                return voice
            time.sleep(0.75)
    finally:
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()
    return None


def stream_ducked_insert(url: str, beds: list[Path], voice: Path) -> None:
    """Play voice at full level over music ducked to DUCK_VOL; duration = voice length."""
    bed = beds[0] if beds else None
    if bed is None:
        cmd = [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "warning",
            "-re",
            "-i",
            str(voice),
            "-filter:a",
            f"volume={VOICE_VOL}",
            "-ac",
            "2",
            "-ar",
            "44100",
            "-c:a",
            "libmp3lame",
            "-b:a",
            "128k",
            "-content_type",
            "audio/mpeg",
            "-f",
            "mp3",
            url,
        ]
        run_ffmpeg(cmd)
        return

    cmd = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "warning",
        "-re",
        "-stream_loop",
        "-1",
        "-i",
        str(bed),
        "-re",
        "-i",
        str(voice),
        "-filter_complex",
        f"[0:a]volume={DUCK_VOL},aformat=sample_rates=44100:channel_layouts=stereo[bed];"
        f"[1:a]volume={VOICE_VOL},aformat=sample_rates=44100:channel_layouts=stereo[vox];"
        f"[bed][vox]amix=inputs=2:duration=shortest:dropout_transition=0[a]",
        "-map",
        "[a]",
        "-ac",
        "2",
        "-ar",
        "44100",
        "-c:a",
        "libmp3lame",
        "-b:a",
        "128k",
        "-content_type",
        "audio/mpeg",
        "-f",
        "mp3",
        url,
    ]
    run_ffmpeg(cmd)


def stream_quiet_bed(url: str) -> None:
    cmd = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "warning",
        "-re",
        "-f",
        "lavfi",
        "-i",
        "anoisesrc=color=pink:amplitude=0.02:sample_rate=44100",
        "-ac",
        "2",
        "-ar",
        "44100",
        "-c:a",
        "libmp3lame",
        "-b:a",
        "96k",
        "-content_type",
        "audio/mpeg",
        "-t",
        "30",
        "-f",
        "mp3",
        url,
    ]
    run_ffmpeg(cmd)


def _play_insert(url: str, beds: list[Path], voice: Path) -> None:
    is_fb = voice.parent.resolve() == FALLBACK.resolve() if FALLBACK.is_dir() else False
    label = "fallback" if is_fb else "report"
    log(f"playing ducked {label} {voice.name}")
    stream_ducked_insert(url, beds, voice)
    if is_fb:
        mark_fallback_played(voice)
    else:
        archive_voice(voice)


def main() -> None:
    url = ice_url()
    AUDIO.mkdir(parents=True, exist_ok=True)
    MEDIA.mkdir(parents=True, exist_ok=True)
    FALLBACK.mkdir(parents=True, exist_ok=True)
    log(
        f"radio_mix start (duck inserts; stale>{STALE_SEC:.0f}s → offline fallbacks "
        f"every {FALLBACK_EVERY_SEC:.0f}s)"
    )
    while True:
        beds = music_files()
        fresh = reports_fresh()
        voice = pending_voice()
        if voice is None and not fresh:
            voice = pending_fallback()
        if voice is not None:
            _play_insert(url, beds, voice)
            continue
        if beds:
            got = stream_music_until_voice(url, beds)
            if got is not None:
                _play_insert(url, beds, got)
            else:
                log("music ffmpeg exited — restart in 2s")
                time.sleep(2)
            continue
        log("no music beds — quiet carrier 30s")
        stream_quiet_bed(url)
        time.sleep(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(0)
