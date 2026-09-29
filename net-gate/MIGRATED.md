# MIGRATED — do not run from here

| Field | Value |
| --- | --- |
| **Status** | Retired (G1 scheduler) |
| **Date** | 2026-09-28 |
| **Superseded by** | Org Pacific Automations internet gate |
| **Canonical repo** | `RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server` |
| **Canonical path** | `Automations/scripts/poller/internet_gate.py` |
| **Related jobs** | `cloudflare_tunnel`, `ensure_tunnel_online` in `jobs.py` |

## What replaced this skill

| This folder (G1) | Production (G3) |
| --- | --- |
| `scripts/net_gate.py` | `Automations/scripts/poller/internet_gate.py` + tunnel builtins |

Offline/online gating and tunnel lifecycle live on G3. Do not restore or run this skill.

Authority: **RootRecord-Software-Solutions** (org).
