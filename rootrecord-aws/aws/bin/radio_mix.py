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
CHIMES = ROOT / "radio" / "chimes"
VOICE_DONE = ROOT / "radio" / "voice-played"
ETC = ROOT / "etc"
LOGS = ROOT / "logs"
PASS_FILE = ETC / "radio.source.password"
META = AUDIO / "Current.meta.json"
FALLBACK_STATE = AUDIO / "fallback-state.json"
CHIME_STATE = AUDIO / "chime-state.json"
try:
    from zoneinfo import ZoneInfo

    HST = ZoneInfo("Pacific/Honolulu")
except Exception:  # pragma: no cover
    HST = None
ICE_HOST = os.environ.get("RR_ICE_HOST", "127.0.0.1")
ICE_PORT = os.environ.get("RR_ICE_PORT", "8000")
MOUNT = os.environ.get("RR_ICE_MOUNT", "/rootrecord.mp3")
MUSIC_VOL = float(os.environ.get("RR_RADIO_MUSIC_VOL", "1.0"))
# Music under voice — keep quiet so reports read clearly
DUCK_VOL = float(os.environ.get("RR_RADIO_DUCK_VOL", "0.18"))
# Voice boost (amix normalize=0 so this is not halved)
VOICE_VOL = float(os.environ.get("RR_RADIO_VOICE_VOL", "2.2"))
# No new report within this many seconds → play offline fallbacks only
STALE_SEC = float(os.environ.get("RR_REPORT_STALE_SEC", str(int(1.5 * 3600))))
# Seconds between offline fallback inserts while stale
FALLBACK_EVERY_SEC = float(os.environ.get("RR_FALLBACK_EVERY_SEC", "900"))
# How often to poll for a newly arrived Current while music plays
POLL_SEC = float(os.environ.get("RR_RADIO_POLL_SEC", "0.35"))
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


def _load_chime_state() -> dict:
    if not CHIME_STATE.is_file():
        return {"last_mark": ""}
    try:
        data = json.loads(CHIME_STATE.read_text(encoding="utf-8"))
        return {"last_mark": str(data.get("last_mark") or "")}
    except (OSError, json.JSONDecodeError, TypeError, ValueError):
        return {"last_mark": ""}


def _save_chime_state(state: dict) -> None:
    AUDIO.mkdir(parents=True, exist_ok=True)
    tmp = CHIME_STATE.with_suffix(".tmp")
    tmp.write_text(json.dumps(state) + "\n", encoding="utf-8")
    tmp.replace(CHIME_STATE)


def pending_chime() -> Path | None:
    """Prebuilt pack clip for :00 / :30 HST — once per mark (date+HHMM)."""
    if HST is None:
        return None
    from datetime import datetime

    now = datetime.now(HST)
    if now.minute not in (0, 30):
        return None
    mark = f"{now.strftime('%Y-%m-%d')}-{now.hour:02d}{now.minute:02d}"
    state = _load_chime_state()
    if state.get("last_mark") == mark:
        return None
    path = CHIMES / f"chime-{now.hour:02d}{now.minute:02d}.wav"
    if not path.is_file() or path.stat().st_size < 44:
        log(f"chime pack miss for {path.name}")
        return None
    return path


def mark_chime_played(path: Path) -> None:
    from datetime import datetime

    now = datetime.now(HST) if HST is not None else None
    if now is not None:
        mark = f"{now.strftime('%Y-%m-%d')}-{now.hour:02d}{now.minute:02d}"
    else:
        mark = path.stem.replace("chime-", "")
    _save_chime_state({"last_mark": mark, "played_at": time.time(), "file": path.name})


def pending_voice() -> Path | None:
    """Newest live report insert (play ASAP). Older backlog archived unplayed."""
    if not reports_fresh():
        discard_stale_currents()
        return None
    cands = _list_current_audio()
    if not cands:
        return None
    # Newest first — just-generated report cuts in immediately
    ordered = sorted(cands, key=lambda p: p.stat().st_mtime, reverse=True)
    newest = ordered[0]
    # Drop older queued Currents so we don't reconnect Icecast dozens of times
    for old in ordered[1:]:
        log(f"skip older queued report {old.name} (playing newest)")
        archive_voice(old)
    return newest


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
            voice = pending_chime()
            if voice is None:
                voice = pending_voice()
            if voice is None:
                voice = pending_fallback()
            if voice is not None:
                if voice.parent.resolve() == CHIMES.resolve():
                    kind = "chime"
                elif voice.parent.resolve() == FALLBACK.resolve():
                    kind = "fallback"
                else:
                    kind = "report"
                log(f"{kind} insert detected {voice.name} — ducking music")
                proc.terminate()
                try:
                    proc.wait(timeout=8)
                except subprocess.TimeoutExpired:
                    proc.kill()
                return voice
            time.sleep(POLL_SEC)
    finally:
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()
    return None


def stream_ducked_insert(url: str, beds: list[Path], voice: Path) -> None:
    """Play voice loud over music ducked hard; duration = voice length.

    amix normalize=0 is required — default amix halves levels and buried reports.
    """
    vox_filter = (
        f"volume={VOICE_VOL},"
        "acompressor=threshold=-20dB:ratio=3:attack=15:release=200:makeup=2,"
        "aformat=sample_rates=44100:channel_layouts=stereo"
    )
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
            vox_filter,
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
        f"[1:a]{vox_filter}[vox];"
        f"[bed][vox]amix=inputs=2:duration=shortest:dropout_transition=0:normalize=0[a]",
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
    parent = voice.parent.resolve()
    if CHIMES.is_dir() and parent == CHIMES.resolve():
        label = "chime"
    elif FALLBACK.is_dir() and parent == FALLBACK.resolve():
        label = "fallback"
    else:
        label = "report"
    log(f"playing ducked {label} {voice.name}")
    stream_ducked_insert(url, beds, voice)
    if label == "chime":
        mark_chime_played(voice)
    elif label == "fallback":
        mark_fallback_played(voice)
    else:
        archive_voice(voice)


def main() -> None:
    url = ice_url()
    AUDIO.mkdir(parents=True, exist_ok=True)
    MEDIA.mkdir(parents=True, exist_ok=True)
    FALLBACK.mkdir(parents=True, exist_ok=True)
    CHIMES.mkdir(parents=True, exist_ok=True)
    n_chimes = len(list(CHIMES.glob("chime-????.wav"))) if CHIMES.is_dir() else 0
    log(
        f"radio_mix start (duck inserts; chimes={n_chimes}/48; "
        f"stale>{STALE_SEC:.0f}s → offline fallbacks every {FALLBACK_EVERY_SEC:.0f}s)"
    )
    while True:
        beds = music_files()
        fresh = reports_fresh()
        voice = pending_chime()
        if voice is None:
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
