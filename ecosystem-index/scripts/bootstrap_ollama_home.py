#!/usr/bin/env python3
"""Foundation: put RootRecord topic skills on ~/.ollama/skills. Do not move runtime Python."""
from __future__ import annotations

import json
import os
import shutil
import sys
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

from rr_home import (  # noqa: E402
    AVA,
    CURSOR_SKILLS,
    DEV_DESK,
    ECOFLOW,
    MEDIA,
    NEVER_IN_OLLAMA,
    OLLAMA_HOME,
    OLLAMA_MODELS,
    OLLAMA_SKILLS,
    OPS,
    REPORTS,
    TOPICS,
    ava_core,
)

HST = ZoneInfo("Pacific/Honolulu")
KEEP = {"skill-creator"}


def now_iso() -> str:
    return datetime.now(HST).isoformat(timespec="seconds")


def copy_tree(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    if src.is_symlink() or src.is_file():
        if dst.exists() or dst.is_symlink():
            dst.unlink()
        shutil.copy2(src, dst, follow_symlinks=False)
        return
    dst.mkdir(parents=True, exist_ok=True)
    for child in src.iterdir():
        copy_tree(child, dst / child.name)


def sync_cursor_into_ollama() -> str:
    OLLAMA_SKILLS.mkdir(parents=True, exist_ok=True)
    src = CURSOR_SKILLS
    if src.is_symlink() and src.resolve() == OLLAMA_SKILLS.resolve():
        return "already_linked"
    if not src.is_dir():
        return "cursor_skills_missing"
    for child in src.iterdir():
        if child.name in KEEP:
            continue
        dest = OLLAMA_SKILLS / child.name
        if dest.exists() or dest.is_symlink():
            if dest.is_dir() and not dest.is_symlink():
                shutil.rmtree(dest)
            else:
                dest.unlink()
        if child.is_symlink():
            dest.symlink_to(os.readlink(child))
        elif child.is_dir():
            shutil.copytree(child, dest, symlinks=True, dirs_exist_ok=False)
        else:
            shutil.copy2(child, dest)
    return "copied"


def replace_cursor_with_symlink() -> str:
    src = CURSOR_SKILLS
    src.parent.mkdir(parents=True, exist_ok=True)
    if src.is_symlink() and src.resolve() == OLLAMA_SKILLS.resolve():
        return "already_linked"
    if not (OLLAMA_SKILLS / "ecosystem-index" / "SKILL.md").is_file():
        raise SystemExit("refuse symlink: ollama skills missing ecosystem-index")
    bak = None
    if src.exists() or src.is_symlink():
        bak = src.parent / f"skills.bak-{datetime.now(HST).strftime('%Y%m%d-%H%M')}"
        src.rename(bak)
    try:
        src.symlink_to(OLLAMA_SKILLS)
    except Exception:
        if bak is not None and bak.exists() and not src.exists():
            bak.rename(src)
        raise
    if bak is not None:
        shutil.rmtree(bak, ignore_errors=True)
    return "linked"


def write_home_docs() -> None:
    (OLLAMA_HOME / "ROOTRECORD.md").write_text(
        f"""# RootRecord home (Ollama)

Generated {now_iso()}.

This directory is RootRecord's **local LLM + skills home**. Models stay in `models/`.
Topic ops live in `skills/`. Ava-Core git/runtime stays at `{AVA}`.

| Path | Role |
| --- | --- |
| `{OLLAMA_HOME}` | Home |
| `{OLLAMA_MODELS}` | GGUF / Ollama blobs (do not put ops here) |
| `{OLLAMA_SKILLS}` | Topic skills (Cursor + Ollama + Bruce) |
| `{AVA}` | Git repo; Ava-Core files are shims into skills |
| `{OPS}` | Core Ops data + vendor; processors are shims |
| `{MEDIA}` | Media library |

Ava-Core `.cursor/skills` is a **symlink** to `{OLLAMA_SKILLS}`.

## Not here

{chr(10).join(f"- `{n}`" for n in NEVER_IN_OLLAMA)}

## Phase

Function desks live under `skills/<function>/scripts/`. Ava-Core / Core Ops copies are shims.
Do not overwrite `skill-creator`.
""",
        encoding="utf-8",
    )
    dest = OLLAMA_SKILLS / "rootrecord-home"
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "SKILL.md").write_text(
        """---
name: rootrecord-home
description: >-
  RootRecord lives under ~/.ollama: models in models/, topic ops in skills/.
  Use when asking where skills go, Ollama home, or before moving a function
  into a skill.
---

# RootRecord home

Carla. Open this skill first when placing files.

Canonical skills: `~/.ollama/skills/<name>/`. Function desks hold runners in
`scripts/`. Topic skills are groupings. Cursor sees the same tree via
`Ava-Core/.cursor/skills` (symlink). Ollama bundled `skill-creator` stays here;
do not overwrite it.

Moved: `fs-index`, `ecoflow-quota`, `rr-kilauea`, `rr-noaa`, `nws-hawaii`, `earthquake-hourly`, `radar-archive`, `official-weather-media`, `hurricane-tracker`, `hurricane-desk`, `hurricane-fetch`, `hurricane-radio`, `hurricane-obs`, `nhc-media`, `kilauea-cams`, `council-quake`, `morning-report`, `morning-report-play`, `merged-morning`, `midday-report`, `midday-report-play`, `late-report`, `late-report-play`, `hourly-chime`, `remaining-tasks`, `morning-boot-replay`, `hourly-clip-reports`, `report-readiness`, `report-periodic-audio`, `day-reports-morning`, `day-reports-midday`, `day-reports-evening`, `day-reports`, `overnight-relay`, `daily-reports-catchup`, `cursor-fallback`, `inbox-drain`, `vercel-builds`, `stripe-poll`, `api-prices`, `economy-brief`, `adsense-eod`, `admob-eod`, `heartbeat`, `minecraft-live`, `player-economy`, `d1-sync`, `user-qrcodes`, `account-import`, `council-bruce-stats`, `governance-daily`, `governance-self-update`, `governance-boot`, `log-cleanup`, `system-perf`, `hourly-solar-weather`, `code-review`, `broadcast-loop`, `boot-prelims`, `day-board-boot`, `recycle-origin`, `ollama-env`, `companions`, `ensure-ava-runtime`, `evening-report`, `evening-report-play`, `evening-report-audio`, `api-prices-boot`, `launch`, `idle-stop`, `ecoflow-ble-poller`, `ecoflow-ac-solar-gate`, `hybrid-night-poller`, `hybrid-reports`, `sunrise-restore`, `report-generation`, `reports`, `daily-report-board`, `startup-voice`, `report-audio-manual`, `report-blog`, `media-library`, `voice-events`, `synth`, `broadcast`, `ollama-lifecycle`, `ollama-client`, `feature-toggles`, `python-drop-runner`, `net-gate`, `uptime-log`, `obs-studio`, `radio`, `xai`, `model-pick`, `live-wx`, `geography`, `load-categories`, `youtube-download`, `finance-desk`, `public-finance`, `rootmc-economy`, `discord`, `slack`, `telegram`, `identities`, `people`, `guests`, `membership`, `subscribers`, `public-chat`, `goals`, `persona`, `look`, `reply-feedback`, `d1`, `mysql`, `rcon`, `db-facts`, `data-layout`, `site-backgrounds`, `site-ops`, `avaivy-cloud`, `rootrecord-online`, `alexrs94-site`, `holding`, `clients`, `fern-forest`, `freeltc`, `cloudflare-workers`, `live-data-pages`, `ops-banner`, `origin-session`, `host-metrics`, `broadcast-render`, `mp4-converter`, `inbox`, `voice`, `origin`, `android-sdk`, `android-build`, `kilauea-alerts`, `rootmc-android`, `rootmc-mobile-web`.

Do not move Media, `.env`, sqlite dumps, or model blobs into a skill.

Read `~/.ollama/ROOTRECORD.md` and `references/layout.json`.""",
        encoding="utf-8",
    )
    (dest / "references").mkdir(parents=True, exist_ok=True)
    layout = {
        "at": now_iso(),
        "phase": "function-desks",
        "moved_runtime": [
            "fs-index",
            "ecoflow-quota",
            "rr-kilauea",
            "rr-noaa",
            "nws-hawaii",
            "earthquake-hourly",
            "radar-archive",
            "official-weather-media",
            "hurricane-tracker",
            "hurricane-desk",
            "hurricane-fetch",
            "hurricane-radio",
            "hurricane-obs",
            "nhc-media",
            "kilauea-cams",
            "council-quake",
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
            "inbox-drain",
            "vercel-builds",
            "stripe-poll",
            "api-prices",
            "economy-brief",
            "adsense-eod",
            "admob-eod",
            "minecraft-live",
            "player-economy",
            "d1-sync",
            "user-qrcodes",
            "account-import",
            "council-bruce-stats",
            "governance-daily",
            "governance-self-update",
            "governance-boot",
            "log-cleanup",
            "system-perf",
            "hourly-solar-weather",
            "code-review",
            "broadcast-loop",
            "boot-prelims",
            "day-board-boot",
            "recycle-origin",
            "evening-report",
            "evening-report-play",
            "evening-report-audio",
            "api-prices-boot",
            "launch",
            "git-auto-push",
            "core-ops-install",
            "idle-stop",
            "ecoflow-ble-poller",
            "ecoflow-ac-solar-gate",
            "ecoflow-river-car",
            "hybrid-night-poller",
            "hybrid-reports",
            "sunrise-restore",
            "report-generation",
            "reports",
            "daily-report-board",
            "startup-voice",
            "report-audio-manual",
            "report-blog",
            "media-library",
            "voice-events",
            "synth",
            "kokoro",
            "broadcast",
            "ollama-lifecycle",
            "ollama-client",
            "feature-toggles",
            "python-drop-runner",
            "net-gate",
            "uptime-log",
            "obs-studio",
            "radio",
            "xai",
            "model-pick",
            "live-wx",
            "geography",
            "load-categories",
            "youtube-download",
            "finance-desk",
            "public-finance",
            "rootmc-economy",
            "discord",
            "slack",
            "telegram",
            "identities",
            "people",
            "guests",
            "membership",
            "subscribers",
            "public-chat",
            "goals",
            "persona",
            "look",
            "bible-prayers",
            "ava-ivy",
            "bruce-monitor",
            "carly-mal",
            "jesus",
            "reply-feedback",
            "d1",
            "mysql",
            "rcon",
            "db-facts",
            "database",
            "state",
            "logs",
            "data-layout",
            "site-backgrounds",
            "site-ops",
            "avaivy-cloud",
            "rootrecord-online",
            "alexrs94-site",
            "holding",
            "clients",
            "fern-forest",
            "freeltc",
            "cloudflare-workers",
            "live-data-pages",
            "ops-banner",
            "origin-session",
            "host-metrics",
            "broadcast-render",
            "mp4-converter",
            "inbox",
            "voice",
            "origin",
            "android-sdk",
            "android-build",
            "kilauea-alerts",
            "rootmc-android",
            "rootmc-mobile-web",
            "ollama-env",
            "companions",
            "ensure-ava-runtime",
            "heartbeat",
            "day-reports",
        ],
        "ollama_home": str(OLLAMA_HOME),
        "skills": str(OLLAMA_SKILLS),
        "models": str(OLLAMA_MODELS),
        "ava_core": str(AVA),
        "core_ops": str(OPS),
        "ecoflow": str(ECOFLOW),
        "reports": str(REPORTS),
        "dev_desk": str(DEV_DESK),
        "media": str(MEDIA),
        "cursor_skills": str(CURSOR_SKILLS),
        "never": list(NEVER_IN_OLLAMA),
        "topics": list(TOPICS),
    }
    (dest / "references" / "layout.json").write_text(
        json.dumps(layout, indent=2) + "\n", encoding="utf-8"
    )
    (OLLAMA_SKILLS / "rootrecord-home" / "INDEX.md").write_text(
        f"# Desk — rootrecord-home\n\nGenerated {now_iso()}.\n\n"
        f"Home: `{OLLAMA_HOME}`\nSkills: `{OLLAMA_SKILLS}`\nAva-Core: `{AVA}`\n",
        encoding="utf-8",
    )


def write_migrate_stubs() -> int:
    n = 0
    for name in TOPICS:
        if name == "rootrecord-home":
            continue
        skill = OLLAMA_SKILLS / name
        if not (skill / "SKILL.md").is_file():
            continue
        refs = skill / "references"
        scripts = skill / "scripts"
        refs.mkdir(parents=True, exist_ok=True)
        scripts.mkdir(parents=True, exist_ok=True)
        migrate = refs / "migrate.md"
        if migrate.is_file():
            head = migrate.read_text(encoding="utf-8", errors="replace")[:500]
            if "mapped, not moved" not in head:
                continue
        index = skill / "INDEX.md"
        index_bit = index.read_text(encoding="utf-8", errors="replace") if index.is_file() else "_INDEX.md missing._\n"
        (refs / "migrate.md").write_text(
            f"""# Migrate `{name}` into this skill

Status: **mapped, not moved**. Runtime still executes from Ava-Core / Core Ops.

When we cut this topic over:

1. Put runners in `scripts/` (Ollama skill shape).
2. Keep `SKILL.md` as the model instructions.
3. Point scheduler/systemd at the new scripts.
4. Leave Media, `.env`, sqlite, and jsonl history where they are.
5. Refresh: `python3 ~/.ollama/skills/ecosystem-index/scripts/refresh-all.py`

## Current live files (from INDEX)

{index_bit}
""",
            encoding="utf-8",
        )
        n += 1
    return n


def main() -> int:
    AVA  # noqa: B018 — ensure ava_core() ran
    ava = ava_core()
    if not (
        (ava / "ns" / "apps" / "core").is_dir()
        or (ava / "repo" / "pyproject.toml").is_file()
        or (ava / "pyproject.toml").is_file()
    ):
        print("ava_core missing", ava, file=sys.stderr)
        return 1
    copied = sync_cursor_into_ollama()
    linked = replace_cursor_with_symlink()
    write_home_docs()
    stubs = write_migrate_stubs()
    print(
        json.dumps(
            {
                "copied": copied,
                "linked": linked,
                "migrate_stubs": stubs,
                "skills": str(OLLAMA_SKILLS),
                "cursor_skills": str(CURSOR_SKILLS),
                "ava_core": str(ava),
            }
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
