# MIGRATED — do not run from here

| Field | Value |
| --- | --- |
| **Status** | Retired (G1 scheduler) |
| **Date** | 2026-09-28 |
| **Superseded by** | Org Pacific Automations builtin |
| **Canonical repo** | `RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server` |
| **Canonical path** | `Automations/scripts/jobs.py` → job id `heartbeat` (`builtin: "heartbeat"`) |
| **Engine** | `Automations/scripts/rootserver_poller.py` |

## What replaced this skill

| This folder (G1) | Production (G3) |
| --- | --- |
| `scripts/heartbeat.py` | Poller builtin `heartbeat` (EVERY_SECONDS, interval 60s) |

ENERGY snapshot is owned by the G3 job catalog. Do not restore or run this skill.

Authority: **RootRecord-Software-Solutions** (org).
