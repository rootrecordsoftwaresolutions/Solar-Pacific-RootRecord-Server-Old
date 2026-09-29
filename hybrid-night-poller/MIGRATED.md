# MIGRATED — do not run from here

| Field | Value |
| --- | --- |
| **Status** | Retired (G1 scheduler) |
| **Date** | 2026-09-28 |
| **Superseded by** | Org Pacific Automations engine |
| **Canonical repo** | `RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server` |
| **Canonical path** | `Automations/scripts/rootserver_poller.py` + `jobs.py` + `poller/` + `stack/` |
| **systemd** | `rr-rootserver-poller.service` (Pacific desk) |

## What replaced this skill

| This folder (G1) | Production (G3) |
| --- | --- |
| `scripts/hybrid_night_poller.py` | `Automations/scripts/rootserver_poller.py` |
| `scripts/ava-hybrid-night.service` | `rr-rootserver-poller.service` |

G3 is the sole live poller host. Do not restore, enable, or schedule this skill.

Authority: **RootRecord-Software-Solutions** (org).
