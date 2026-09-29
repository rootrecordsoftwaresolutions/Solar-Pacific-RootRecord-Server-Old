# MIGRATED — do not run from here

| Field | Value |
| --- | --- |
| **Status** | Retired (G1 reports packet — worklog path) |
| **Date** | 2026-09-28 |
| **Superseded by** | Org Pacific **Reports/** domain (WO-RPT-001 Phase B LIVE) |
| **Canonical repo** | `RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server` |
| **Canonical path** | `Reports/scripts/worklog_once.sh` + `worklog_lib.sh` |
| **jobs.py** | `worklog_scan` → Pacific `Reports/scripts/worklog_once.sh` |
| **Data** | `/home/rootrecord/Database/WORKLOG/` (unchanged) |
| **Work order** | [WO-RPT-001](https://github.com/RootRecord-Software-Solutions/RootRecord-Library/blob/main/Documentation/06-development/Work-Orders/WO-RPT-001-Reports-Worklog-Domain-Import.md) |

## What replaced this skill

| This folder (G1) | Production (G3) |
| --- | --- |
| Offline work auto-doc / report store intent | `Reports/` domain on Pacific |
| (G2 residual was live path) `worklog_*.sh` | `Reports/scripts/worklog_*.sh` |

**Note:** G1 `reports.py` public draft-queue complexity was **not** bulk-imported. Diff-only recovery later if needed. Cousins (`hourly-clip-reports/`, `day-board-boot/`, `merged-morning/`) remain NOT MIGRATED — not owned by Reports worklog.

G3 is the sole live worklog path. Do not restore, enable, or schedule this skill as a poller host.

Authority: **RootRecord-Software-Solutions** (org).
