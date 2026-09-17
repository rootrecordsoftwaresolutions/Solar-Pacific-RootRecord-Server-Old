#!/usr/bin/env python3
"""Rebuild each topic skill desk/: links to function desks + live outputs.

Runners live in ~/.ollama/skills/<fn>/scripts/. Ava-Core / Core Ops copies are shims.
The skill folder is the one place agents open. Do not copy secrets or jsonl history.
"""
from __future__ import annotations

import os
import shutil
import sys
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from build_catalog import (  # noqa: E402
    AVA,
    MEDIA,
    ECOFLOW,
    OPS,
    REPORTS,
    SKILLS,
    TOPICS,
    collect_py,
    is_personal,
    now_iso,
    resolve_root,
)

CODE_SUFFIX = {
    ".py",
    ".sh",
    ".service",
    ".desktop",
    ".mdc",
    ".kt",
    ".kts",
    ".ts",
    ".toml",
    ".md",
}
SKIP_PARTS = {
    "__pycache__",
    ".venv",
    "node_modules",
    "vendor",
    ".git",
    "pb",
    "history",
    "quota",
    "build",
    "intermediates",
    ".gradle",
    ".wrangler",
    "wrangler-out",
    "dist",
}
SKIP_NAMES = {"CURRENT.md", "DAILY.md", "INDEX.md", "paths.txt", "dir-mtimes.json"}


def _ordinal(day: int) -> str:
    if 11 <= day % 100 <= 13:
        return "th"
    return {1: "st", 2: "nd", 3: "rd"}.get(day % 10, "th")


def hybrid_today() -> Path | None:
    now = datetime.now()
    try:
        from zoneinfo import ZoneInfo

        now = datetime.now(ZoneInfo("Pacific/Honolulu"))
    except Exception:
        pass
    month = now.strftime("%B")
    folder = f"{month} {now.day}{_ordinal(now.day)}, {now.year}"
    path = (
        REPORTS
        / str(now.year)
        / month
        / folder
        / f"hybrid-manual-daily-report-{now:%Y-%m-%d}.md"
    )
    return path if path.is_file() else None


def collect_code(root: Path, cap: int = 400) -> list[Path]:
    if root.is_file():
        if root.name in SKIP_NAMES:
            return []
        if root.suffix.lower() in CODE_SUFFIX and not is_personal(root):
            return [root]
        return []
    if not root.is_dir():
        return []
    out: list[Path] = []
    for p in sorted(root.rglob("*")):
        if not p.is_file() or is_personal(p):
            continue
        if p.name in SKIP_NAMES:
            continue
        if any(part in SKIP_PARTS for part in p.parts):
            continue
        if p.suffix.lower() not in CODE_SUFFIX:
            continue
        out.append(p)
        if len(out) >= cap:
            break
    return out


def topic_live(name: str) -> list[Path]:
    reports = MEDIA / "documents" / "reports"
    data_state = Path.home() / ".ollama" / "skills" / "state" / "store"
    eco = ECOFLOW
    extra: list[Path] = []
    current_reports = [
        "kilauea-current.md",
        "nws-hawaii-counties-current.md",
        "morning-report-current.md",
        "midday-boot-current.md",
        "morning-boot-current.md",
        "evening-current.md",
    ]
    if name in {"weather-kilauea", "media-hybrid", "reports-voice"}:
        extra += [reports / n for n in current_reports]
    if name in {"weather-kilauea", "media-hybrid", "ecoflow-automations", "reports-voice", "boot-idle-origin"}:
        ht = hybrid_today()
        if ht:
            extra.append(ht)
        extra.append(SKILLS / "hybrid-reports" / "scripts" / "hybrid_reports.py")
        extra.append(SKILLS / "hybrid-night-poller" / "scripts" / "hybrid_night_poller.py")
        extra.append(SKILLS / "hybrid-reports" / "SKILL.md")
    if name == "scheduler-clock":
        extra += [
            SKILLS / "scheduler-clock" / "scripts" / "scheduler.py",
            SKILLS / "scheduler-clock" / "scripts" / "schedule_clock.py",
        ]
    if name == "council-telegram":
        extra += [
            SKILLS / "council-telegram" / "scripts" / "desk_read.py",
            SKILLS / "council-telegram" / "scripts" / "personas.py",
            SKILLS / "council-bruce-stats" / "scripts" / "job.py",
            SKILLS / "governance-daily" / "scripts" / "job.py",
            SKILLS / "governance-self-update" / "scripts" / "job.py",
            SKILLS / "governance-boot" / "scripts" / "job.py",
            SKILLS / "council-quake" / "scripts" / "quake_watch.py",
            SKILLS / "governance-daily" / "scripts" / "governance.py",
            SKILLS / "telegram" / "scripts" / "telegram.py",
            SKILLS / "telegram" / "scripts" / "telegram_rooms.py",
            SKILLS / "persona" / "scripts" / "persona.py",
            SKILLS / "look" / "scripts" / "look.py",
        ]
    if name in {"ecoflow-automations", "boot-idle-origin", "ava-ops"}:
        extra += [
            eco / "state" / "ecoflow-live.json",
            data_state / "feature-toggles.json",
        ]
    if name == "boot-idle-origin":
        extra += [
            SKILLS / "launch" / "scripts" / "launch.sh",
            SKILLS / "launch" / "scripts" / "install.sh",
            SKILLS / "core-ops-install" / "scripts" / "install.sh",
            SKILLS / "core-ops-install" / "scripts" / "boot.py",
            SKILLS / "idle-stop" / "scripts" / "idle-stop.sh",
            SKILLS / "log-cleanup" / "scripts" / "job.py",
            SKILLS / "code-review" / "scripts" / "job.py",
            SKILLS / "boot-prelims" / "scripts" / "job.py",
            SKILLS / "day-board-boot" / "scripts" / "job.py",
            SKILLS / "recycle-origin" / "scripts" / "recycle-origin.sh",
            SKILLS / "ollama-env" / "scripts" / "ollama-env.sh",
            SKILLS / "companions" / "scripts" / "start-ava-companions.sh",
            SKILLS / "companions" / "scripts" / "start-ava-desktop.sh",
            SKILLS / "companions" / "scripts" / "start-dev-desk.sh",
            SKILLS / "ensure-ava-runtime" / "scripts" / "ensure-ava-runtime.sh",
            SKILLS / "ollama-lifecycle" / "scripts" / "ollama_lifecycle.py",
            SKILLS / "ollama-client" / "scripts" / "ollama.py",
            SKILLS / "uptime-log" / "scripts" / "uptime_log.py",
            SKILLS / "live-data-pages" / "scripts" / "live_data_pages.py",
            SKILLS / "origin-session" / "scripts" / "origin_session.py",
            SKILLS / "host-metrics" / "scripts" / "host_metrics.py",
            SKILLS / "scheduler-clock" / "scripts" / "scheduler.py",
            SKILLS / "scheduler-clock" / "scripts" / "schedule_clock.py",
            SKILLS / "origin" / "scripts" / "main.py",
            SKILLS / "origin" / "scripts" / "config.py",
            SKILLS / "git-auto-push" / "scripts" / "auto-push.sh",
            SKILLS / "git-auto-push" / "scripts" / "ava-github-push.mjs",
        ]
    if name == "ecoflow-automations":
        extra.append(SKILLS / "ecoflow-quota" / "scripts" / "ecoflow_quota.py")
        extra.append(SKILLS / "ecoflow-ble-poller" / "scripts" / "ecoflow_ble_poller.py")
        extra.append(SKILLS / "ecoflow-ac-solar-gate" / "scripts" / "ecoflow_ac_solar_gate.py")
        extra.append(SKILLS / "ecoflow-river-car" / "scripts" / "river_car_dc.py")
        extra.append(SKILLS / "ecoflow-river-car" / "scripts" / "drive_automation.py")
        extra.append(SKILLS / "hourly-solar-weather" / "scripts" / "job.py")
        extra.append(SKILLS / "sunrise-restore" / "scripts" / "sunrise_restore.py")
        extra.append(SKILLS / "ecoflow-quota" / "scripts" / "energy.py")
        extra.append(SKILLS / "ecoflow-automations" / "scripts" / "ecoflow_delta2_power_test.py")
        extra.append(SKILLS / "hourly-solar-weather" / "scripts" / "sun_times.py")
        extra.append(SKILLS / "load-categories" / "scripts" / "load_categories.py")
        extra.append(SKILLS / "ecoflow-quota" / "scripts" / "ecoflow_public.py")
        extra.append(SKILLS / "ecoflow-ble-poller" / "scripts" / "ecoflow_ble_store.py")
        extra.append(SKILLS / "hybrid-night-poller" / "scripts" / "hybrid_night_poller.py")
        extra += [
            eco / "state" / "night-mode.json",
            eco / "state" / "ecoflow-ac-solar-gate.json",
            eco / "state" / "sun-times.json",
            eco / "README.md",
            eco / "README-BLE.md",
        ]
    if name == "ava-ops":
        extra += [
            SKILLS / "system-perf" / "scripts" / "job.py",
            SKILLS / "broadcast-loop" / "scripts" / "job.py",
            SKILLS / "broadcast" / "scripts" / "broadcast.py",
            SKILLS / "feature-toggles" / "scripts" / "feature_toggles.py",
            SKILLS / "python-drop-runner" / "scripts" / "python_drop_runner.py",
            SKILLS / "net-gate" / "scripts" / "net_gate.py",
            SKILLS / "obs-studio" / "scripts" / "obs_studio.py",
            SKILLS / "obs-studio" / "scripts" / "obs_desk_data.py",
            SKILLS / "obs-studio" / "store" / "obs-quake-feed.json",
            SKILLS / "obs-studio" / "scripts" / "obs_overlay_gen.py",
            SKILLS / "obs-studio" / "scripts" / "solar_obs_server.py",
            SKILLS / "obs-studio" / "scripts" / "solar_monitor.py",
            SKILLS / "obs-studio" / "scripts" / "new.py",
            SKILLS / "obs-studio" / "scripts" / "obs_presence.py",
            SKILLS / "obs-studio" / "scripts" / "obs_scene_visibility.py",
            SKILLS / "radio" / "scripts" / "radio.py",
            SKILLS / "radio" / "scripts" / "radio_access.py",
            SKILLS / "radio" / "scripts" / "radio_catalog.py",
            SKILLS / "radio" / "scripts" / "radio_encode.py",
            SKILLS / "discord" / "scripts" / "discord.py",
            SKILLS / "discord" / "scripts" / "discord_chat.py",
            SKILLS / "slack" / "scripts" / "slack.py",
            SKILLS / "goals" / "scripts" / "goals.py",
            SKILLS / "goals" / "scripts" / "goal_drafts.py",
            SKILLS / "ops-banner" / "scripts" / "ops_banner.py",
            SKILLS / "ava-ops" / "scripts" / "ava-ops.sh",
            SKILLS / "ava-ops" / "scripts" / "ava_bt_bridge.py",
            SKILLS / "android-sdk" / "scripts" / "sdk_status.py",
            SKILLS / "android-sdk" / "scripts" / "android-env.sh",
        ]
    if name == "android-sdk":
        extra += [
            SKILLS / "android-sdk" / "scripts" / "sdk_status.py",
            SKILLS / "android-sdk" / "scripts" / "android-env.sh",
        ]
    if name == "reports-voice":
        extra += [
            SKILLS / n / "scripts" / "job.py"
            for n in (
                "morning-report",
                "morning-report-play",
                "merged-morning",
                "midday-report",
                "midday-report-play",
                "late-report",
                "late-report-play",
                "hourly-chime",
                "remaining-tasks",
                "morning-boot-replay",
                "hourly-clip-reports",
                "report-readiness",
                "report-periodic-audio",
                "day-reports-morning",
                "day-reports-midday",
                "day-reports-evening",
                "overnight-relay",
                "daily-reports-catchup",
                "cursor-fallback",
                "evening-report",
                "evening-report-play",
                "evening-report-audio",
            )
        ]
        extra.append(SKILLS / "day-reports" / "scripts" / "day_reports.py")
        extra.append(SKILLS / "report-periodic-audio" / "scripts" / "report_periodic_audio.py")
        extra.append(SKILLS / "morning-report" / "scripts" / "boot_report.py")
        extra.append(SKILLS / "midday-report" / "scripts" / "midday_report.py")
        extra.append(SKILLS / "report-generation" / "scripts" / "report_generation.py")
        extra.append(SKILLS / "reports" / "scripts" / "reports.py")
        extra.append(SKILLS / "daily-report-board" / "scripts" / "daily_report_board.py")
        extra.append(SKILLS / "startup-voice" / "scripts" / "startup_voice.py")
        extra.append(SKILLS / "remaining-tasks" / "scripts" / "day_board.py")
        extra.append(SKILLS / "cursor-fallback" / "scripts" / "cursor_fallback.py")
        extra.append(SKILLS / "report-audio-manual" / "scripts" / "report_audio_manual.py")
        extra.append(SKILLS / "report-blog" / "scripts" / "report_blog.py")
        extra.append(SKILLS / "report-blog" / "scripts" / "sync-blogs.py")
        extra.append(SKILLS / "report-blog" / "scripts" / "compile-blog-posts.py")
        extra.append(SKILLS / "voice-events" / "scripts" / "voice_events.py")
        extra.append(SKILLS / "synth" / "scripts" / "synth.py")
        extra.append(SKILLS / "kokoro" / "scripts" / "kokoro_tts.py")
        extra.append(SKILLS / "kokoro" / "scripts" / "speakers.py")
        extra.append(SKILLS / "kokoro" / "scripts" / "speakable.py")
        extra.append(SKILLS / "kokoro" / "scripts" / "hawaiian_lexicon.py")
        extra.append(SKILLS / "broadcast" / "scripts" / "broadcast.py")
        extra.append(SKILLS / "xai" / "scripts" / "xai.py")
        extra.append(SKILLS / "model-pick" / "scripts" / "model_pick.py")
        extra.append(SKILLS / "reply-feedback" / "scripts" / "reply_feedback.py")
        extra.append(SKILLS / "reply-feedback" / "scripts" / "feedback_store.py")
        extra.append(SKILLS / "voice" / "scripts" / "director.py")
        extra.append(SKILLS / "broadcast-render" / "scripts" / "broadcast_render.py")
        extra.append(SKILLS / "mp4-converter" / "scripts" / "mp4_converter.py")
    if name == "rootmc":
        extra += [
            SKILLS / n / "scripts" / "job.py"
            for n in (
                "minecraft-live",
                "player-economy",
                "d1-sync",
                "user-qrcodes",
                "account-import",
            )
        ]
        extra.append(SKILLS / "minecraft-live" / "scripts" / "minecraft_live.py")
        extra.append(SKILLS / "account-import" / "scripts" / "account_import.py")
        extra.append(SKILLS / "user-qrcodes" / "scripts" / "user_qrcodes.py")
        extra.append(SKILLS / "rootmc-economy" / "scripts" / "rootmc_economy.py")
        extra.append(SKILLS / "d1" / "scripts" / "d1.py")
        extra.append(SKILLS / "rcon" / "scripts" / "rcon.py")
        extra.append(SKILLS / "rootmc" / "scripts" / "build-app-icon.py")
        extra.append(SKILLS / "rootmc" / "scripts" / "build-feature-graphic.py")
        extra.append(SKILLS / "rootmc" / "scripts" / "build-splash.py")
        extra.append(SKILLS / "rootmc" / "scripts" / "load-rootmc-env.sh")
        extra.append(SKILLS / "rootmc" / "scripts" / "ops" / "reconcile-treasury-ledger.py")
    if name == "public-edge":
        extra += [
            SKILLS / n / "scripts" / "job.py"
            for n in (
                "inbox-drain",
                "vercel-builds",
                "stripe-poll",
                "api-prices",
                "economy-brief",
                "adsense-eod",
                "admob-eod",
            )
        ]
        extra.append(SKILLS / "heartbeat" / "scripts" / "heartbeat.py")
        extra.append(SKILLS / "api-prices-boot" / "scripts" / "job.py")
        extra.append(SKILLS / "inbox-drain" / "scripts" / "offline_inbox.py")
        extra.append(SKILLS / "vercel-builds" / "scripts" / "vercel_builds.py")
        extra.append(SKILLS / "stripe-poll" / "scripts" / "stripe_poll.py")
        extra.append(SKILLS / "adsense-eod" / "scripts" / "adsense.py")
        extra.append(SKILLS / "admob-eod" / "scripts" / "admob.py")
        extra.append(SKILLS / "api-prices" / "scripts" / "api_ledger.py")
        extra.append(SKILLS / "finance-desk" / "scripts" / "finance_desk.py")
        extra.append(SKILLS / "finance-desk" / "scripts" / "extract_bm_money_sqlite.py")
        extra.append(SKILLS / "finance-desk" / "scripts" / "import_windows_bm_sqlite.py")
        extra.append(SKILLS / "public-finance" / "scripts" / "public_finance.py")
        extra.append(SKILLS / "subscribers" / "scripts" / "subscribers.py")
        extra.append(SKILLS / "public-chat" / "scripts" / "public_chat.py")
        extra.append(SKILLS / "site-backgrounds" / "scripts" / "site_backgrounds.py")
        extra.append(SKILLS / "site-ops" / "scripts" / "site_ops.py")
        extra.append(SKILLS / "site-ops" / "scripts" / "generate-bm-icon.py")
        extra.append(SKILLS / "site-ops" / "scripts" / "fix-encoding-html.py")
        extra.append(SKILLS / "site-ops" / "scripts" / "insert-my-apps-nav.py")
        extra.append(SKILLS / "public-edge" / "scripts" / "deploy-next-to-pages.sh")
        extra.append(SKILLS / "public-edge" / "scripts" / "rootrecord-static-server.py")
        extra.append(SKILLS / "inbox" / "scripts" / "inbox.py")
        extra.append(SKILLS / "avaivy-cloud" / "site" / "package.json")
        extra.append(SKILLS / "rootrecord-online" / "site" / "package.json")
        extra.append(SKILLS / "alexrs94-site" / "site" / "package.json")
        extra.append(SKILLS / "holding" / "site" / "index.html")
        extra.append(SKILLS / "clients" / "gigs" / "nibble.love" / "index.html")
        extra.append(SKILLS / "freeltc" / "site" / "index.html")
        extra.append(SKILLS / "cloudflare-workers" / "workers" / "package.json")
    if name == "clients":
        extra.append(SKILLS / "clients" / "gigs" / "nibble.love" / "README.md")
    if name == "fern-forest":
        extra.append(SKILLS / "fern-forest" / "SKILL.md")
    if name == "freeltc":
        extra.append(SKILLS / "freeltc" / "site" / "index.html")
    if name == "media-hybrid":
        extra.append(SKILLS / "hybrid-reports" / "scripts" / "hybrid_reports.py")
        extra.append(SKILLS / "media-library" / "scripts" / "convert_media_library.py")
        extra.append(SKILLS / "media-library" / "scripts" / "media_library.py")
        extra.append(SKILLS / "media-library" / "scripts" / "consolidate_media.sh")
        extra.append(SKILLS / "youtube-download" / "scripts" / "youtube_download.py")
    if name == "live-directories":
        extra.append(SKILLS / "fs-index" / "scripts" / "incremental_fs_index.py")
    if name == "desk-data-reader":
        extra.append(HERE / "refresh_data_maps.py")
        extra.append(SKILLS / "identities" / "scripts" / "identities.py")
        extra.append(SKILLS / "people" / "scripts" / "people.py")
        extra.append(SKILLS / "guests" / "scripts" / "guests.py")
        extra.append(SKILLS / "membership" / "scripts" / "membership.py")
        extra.append(SKILLS / "mysql" / "scripts" / "mysql.py")
        extra.append(SKILLS / "db-facts" / "scripts" / "db_facts.py")
        extra.append(SKILLS / "data-layout" / "scripts" / "data_layout.py")
        extra.append(SKILLS / "database" / "scripts" / "paths.py")
    if name == "weather-kilauea":
        extra.append(SKILLS / "rr-kilauea" / "scripts" / "kilauea.py")
        extra.append(SKILLS / "rr-noaa" / "scripts" / "weather.py")
        extra.append(SKILLS / "nws-hawaii" / "scripts" / "nws_hawaii.py")
        extra.append(SKILLS / "earthquake-hourly" / "scripts" / "earthquake_hourly.py")
        extra.append(SKILLS / "radar-archive" / "scripts" / "radar_archive.py")
        extra.append(SKILLS / "official-weather-media" / "scripts" / "official_weather_media.py")
        extra.append(SKILLS / "official-weather-media" / "scripts" / "weatherGifLoop.py")
        extra.append(SKILLS / "hurricane-tracker" / "scripts" / "hurricane_tracker.py")
        extra.append(SKILLS / "hurricane-desk" / "scripts" / "hurricane_desk.py")
        extra.append(SKILLS / "nhc-media" / "scripts" / "nhc_media.py")
        extra.append(SKILLS / "kilauea-cams" / "scripts" / "kilauea_cams.py")
        extra.append(SKILLS / "council-quake" / "scripts" / "quake_watch.py")
        extra.append(SKILLS / "live-wx" / "scripts" / "live_wx.py")
        extra.append(SKILLS / "geography" / "scripts" / "geography.py")
        extra += [
            data_state / "kilauea-alert.json",
            data_state / "nws-hawaii.json",
            data_state / "hurricane-desk.json",
            data_state / "sun-times.json",
        ]
    return extra


def files_for_topic(topic: dict) -> list[Path]:
    seen: set[Path] = set()
    out: list[Path] = []
    cap = 400
    for rel in topic.get("roots") or []:
        root = resolve_root(rel)
        chunk = collect_code(root, cap=cap)
        if root.is_file() and root.suffix == ".py":
            chunk = collect_py(root, cap_files=cap) or chunk
        for p in chunk:
            rp = p.resolve()
            if rp in seen:
                continue
            seen.add(rp)
            out.append(rp)
    for absr in topic.get("abs_roots") or []:
        p = Path(absr)
        if p.name == "Reports":
            continue
        for f in collect_code(p, cap=cap):
            rp = f.resolve()
            if rp in seen:
                continue
            seen.add(rp)
            out.append(rp)
    for extra in topic_live(topic["name"]):
        if extra.is_file():
            rp = extra.resolve()
            if rp not in seen:
                seen.add(rp)
                out.append(rp)
    return out


def link_file(src: Path, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.is_symlink() or dest.exists():
        dest.unlink()
    dest.symlink_to(os.path.relpath(src, dest.parent))


def place(src: Path, desk: Path) -> Path:
    src = src.resolve()
    try:
        rel = src.relative_to(AVA)
        dest = desk / "src" / rel
    except ValueError:
        try:
            rel = src.relative_to(OPS)
            dest = desk / "ops" / rel
        except ValueError:
            dest = desk / "live" / src.name
            if dest.exists() or dest.is_symlink():
                dest = desk / "live" / f"{src.parent.name}-{src.name}"
    link_file(src, dest)
    return dest


def write_index(skill: Path, rows: list[tuple[Path, Path]]) -> None:
    lines = [
        f"# Desk — {skill.name}",
        "",
        f"Generated {now_iso()}. This folder is the ops desk for the topic.",
        "",
        "Open **this skill directory**. `desk/src` and `desk/ops` map related files.",
        "Runners live in `~/.ollama/skills/<fn>/scripts/` (Ava-Core / Core Ops copies are shims).",
        "`DAILY.md` is the processed hybrid-style summary. `CURRENT.md` is the map.",
        "Facts from this desk. `.env` stays closed.",
        "",
        "| In this desk | Live path |",
        "| --- | --- |",
    ]
    for dest, src in sorted(rows, key=lambda r: str(r[0])):
        try:
            rel = dest.relative_to(skill)
        except ValueError:
            rel = dest
        lines.append(f"| `{rel}` | `{src}` |")
    lines.append("")
    (skill / "INDEX.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (skill / "desk" / "README.md").write_text(
        f"Symlinked runners for `{skill.name}`. See `../INDEX.md`.\n",
        encoding="utf-8",
    )


def rebuild_one(name: str, files: list[Path], live: list[Path]) -> int:
    skill = SKILLS / name
    skill.mkdir(parents=True, exist_ok=True)
    desk = skill / "desk"
    if desk.exists():
        shutil.rmtree(desk)
    desk.mkdir(parents=True)
    rows: list[tuple[Path, Path]] = []
    seen: set[Path] = set()
    for src in files + live:
        if not src.is_file() or is_personal(src):
            continue
        if src.suffix.lower() in {".jsonl", ".env"}:
            continue
        if "quota" in src.parts and src.suffix == ".json":
            continue
        rp = src.resolve()
        if rp in seen:
            continue
        seen.add(rp)
        dest = place(src, desk)
        rows.append((dest, rp))
    write_index(skill, rows)
    daily = skill / "DAILY.md"
    if not daily.is_file():
        daily.write_text(
            f"# {name} — hybrid lines\n\n"
            "Filled by `solar-notes-quarter-hour` from the hybrid notebook.\n\n"
            "_No hybrid lines yet today._\n",
            encoding="utf-8",
        )
    return len(rows)


def main() -> int:
    n = 0
    for topic in TOPICS:
        n += rebuild_one(topic["name"], files_for_topic(topic), topic_live(topic["name"]))
    print(f"desk links={n}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
