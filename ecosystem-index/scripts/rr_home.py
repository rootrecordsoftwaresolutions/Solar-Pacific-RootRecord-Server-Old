#!/usr/bin/env python3
"""RootRecord home paths. Skills live under Ollama; origin is the runtime tree."""
from __future__ import annotations

import os
from pathlib import Path

HOME = Path.home()
OLLAMA_HOME = Path(os.environ.get("OLLAMA_HOME") or (HOME / ".ollama")).expanduser().resolve()
OLLAMA_SKILLS = OLLAMA_HOME / "skills"
OLLAMA_MODELS = OLLAMA_HOME / "models"
MEDIA = Path(os.environ.get("AVA_MEDIA_DIR") or (HOME / "Media"))

NEVER_IN_OLLAMA = (
    ".env",
    "credentials.env",
    "credentials.json",
    "Media/",
    "quota/{serial}.json",
)


def ava_core() -> Path:
    origin = OLLAMA_SKILLS / "origin"
    for raw in (
        os.environ.get("AVA_CORE"),
        os.environ.get("AVA_HOME"),
        str(origin),
    ):
        if not raw:
            continue
        p = Path(raw).expanduser().resolve()
        if (p / "ns" / "apps" / "core").is_dir():
            return p
        if (p / "pyproject.toml").is_file() and (p / "ns" / "apps" / "core").is_dir():
            return p
        if (p / "repo" / "pyproject.toml").is_file():
            return p
        if (p / "apps" / "core").is_dir():
            return p
    return origin


AVA = ava_core()
CURSOR_SKILLS = (OLLAMA_SKILLS / "origin" / "repo" / ".cursor" / "skills")


def skills_root() -> Path:
    """Canonical topic skills. Ollama first once bootstrapped."""
    if (OLLAMA_SKILLS / "ecosystem-index" / "SKILL.md").is_file():
        return OLLAMA_SKILLS
    if CURSOR_SKILLS.is_dir():
        return CURSOR_SKILLS.resolve()
    return OLLAMA_SKILLS


SKILLS = skills_root()
ECOFLOW = SKILLS / "ecoflow-ble-poller" / "store"
REPORTS = SKILLS / "hybrid-reports" / "store" / "Reports"
DEV_DESK = SKILLS / "companions" / "dev-desk"
OPS = SKILLS / "core-ops-install" / "store"

TOPICS = (
    "scheduler-clock",
    "boot-idle-origin",
    "launch",
    "git-auto-push",
    "core-ops-install",
    "idle-stop",
    "log-cleanup",
    "system-perf",
    "hourly-solar-weather",
    "code-review",
    "broadcast-loop",
    "boot-prelims",
    "day-board-boot",
    "recycle-origin",
    "ollama-env",
    "companions",
    "ensure-ava-runtime",
    "ollama-lifecycle",
    "ollama-client",
    "feature-toggles",
    "python-drop-runner",
    "net-gate",
    "uptime-log",
    "obs-studio",
    "radio",
    "sunrise-restore",
    "reports-voice",
    "evening-report",
    "evening-report-play",
    "evening-report-audio",
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
    "day-reports",
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
    "xai",
    "model-pick",
    "overnight-relay",
    "daily-reports-catchup",
    "cursor-fallback",
    "weather-kilauea",
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
    "live-wx",
    "geography",
    "council-quake",
    "ecoflow-automations",
    "ecoflow-quota",
    "ecoflow-ble-poller",
    "ecoflow-ac-solar-gate",
    "ecoflow-river-car",
    "hybrid-night-poller",
    "hybrid-reports",
    "load-categories",
    "youtube-download",
    "council-telegram",
    "ava-ivy",
    "bruce-monitor",
    "carly-mal",
    "council-bruce-stats",
    "governance-daily",
    "governance-self-update",
    "governance-boot",
    "ava-ops",
    "android-sdk",
    "android-build",
    "kilauea-alerts",
    "rootmc-android",
    "rootmc-mobile-web",
    "rootmc",
    "minecraft-live",
    "player-economy",
    "d1-sync",
    "user-qrcodes",
    "account-import",
    "rootmc-economy",
    "public-edge",
    "inbox-drain",
    "vercel-builds",
    "stripe-poll",
    "api-prices",
    "api-prices-boot",
    "economy-brief",
    "adsense-eod",
    "admob-eod",
    "heartbeat",
    "finance-desk",
    "public-finance",
    "media-hybrid",
    "ecosystem-history",
    "desk-data-reader",
    "live-directories",
    "fs-index",
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
    "jesus",
    "history",
    "cooking",
    "nutrition",
    "pantry",
    "gardening",
    "root-record-registry",
    "research-oa",
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
    "ecosystem-index",
    "rootrecord-home",
)
