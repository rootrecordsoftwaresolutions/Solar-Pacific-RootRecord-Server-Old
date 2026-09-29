# Solar-Pacific-RootRecord-Server-Old

> **G1 historical skill archive — not production.**  
> Do not run this tree as a poller host. Do not bulk-merge into live Pacific.

| Field | Value |
| --- | --- |
| **Generation** | **G1** (oldest skill-packet layout) |
| **Role** | Forensic / selective recovery source only |
| **Production authority** | [RootRecord-Software-Solutions](https://github.com/RootRecord-Software-Solutions) |
| **Live runtime** | [RootRecord-Pacific-Solar-Server](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server) |
| **Docs / agent context** | [RootRecord-Library](https://github.com/RootRecord-Software-Solutions/RootRecord-Library) |
| **Data & logs** | [RootRecord-Database](https://github.com/RootRecord-Software-Solutions/RootRecord-Database) |
| **Last migration pass** | 2026-09-28 HST |

---

## What this repository is

This is the **Old** Solar Pacific skill tree: dozens of top-level packets (`SKILL.md`, `DAILY.md`, `scripts/`, …). It is a **different shape** from:

| Gen | Repo | Layout |
| --- | --- | --- |
| **G3** (live) | [`RootRecord-Pacific-Solar-Server`](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server) | Domain folders: `Automations/`, `Energy/`, `System/`, … |
| **G2** (residual) | [`Solar-Pacific-RootRecord-Server`](https://github.com/rootrecordsoftwaresolutions/Solar-Pacific-RootRecord-Server) | Lowercase skills under desk `~/.ollama/skills` |
| **G1** (this repo) | `Solar-Pacific-RootRecord-Server-Old` | Historical skill packets |

**Org is authority.** Auto-sync from catalogued GitHub remotes updates the desk; this repo is read-mostly archive.

---

## Migration operations (definitive)

### Rules

1. **Never** bulk-merge this repo (especially `origin/`) into G3.  
2. **Never** point `rr-rootserver-poller` or `jobs.py` at paths in this repo.  
3. Import order: finish **G2 residual domains → G3** first; only then selectively recover from G1.  
4. When a packet is fully superseded: keep folder + `SKILL.md`, add **`MIGRATED.md`**, do not run.  
5. One domain / packet at a time; document both here and in Library.  
6. Secrets stay local — strip before any copy into G3.

### How to mark a packet migrated

```text
1. Confirm capability live on org Pacific (or explicitly abandoned).
2. Add MIGRATED.md at packet root (status, date, canonical URL + path).
3. Update the status table in THIS README.
4. Update Library: Solar-Pacific-Old-Inventory-Map + MIGRATION-DOCS-INDEX.
5. Do not delete the folder shell unless operator orders a hard purge.
```

### Canonical Library docs

| Doc | URL |
| --- | --- |
| Migration index | [MIGRATION-DOCS-INDEX-2026-09-28.md](https://github.com/RootRecord-Software-Solutions/RootRecord-Library/blob/main/Documentation/00-architecture/MIGRATION-DOCS-INDEX-2026-09-28.md) |
| Three generations | [Migration-Lineage-Three-Generations-2026-09-28.md](https://github.com/RootRecord-Software-Solutions/RootRecord-Library/blob/main/Documentation/00-architecture/Migration-Lineage-Three-Generations-2026-09-28.md) |
| G1 inventory map | [Solar-Pacific-Old-Inventory-Map-2026-09-28.md](https://github.com/RootRecord-Software-Solutions/RootRecord-Library/blob/main/Documentation/00-architecture/Solar-Pacific-Old-Inventory-Map-2026-09-28.md) |
| Full top-level catalog | [Solar-Pacific-Old-Full-TopLevel-Catalog-2026-09-28.md](https://github.com/RootRecord-Software-Solutions/RootRecord-Library/blob/main/Documentation/00-architecture/Solar-Pacific-Old-Full-TopLevel-Catalog-2026-09-28.md) |
| Import playbook | [Pacific-Domain-Import-Playbook-2026-09-28.md](https://github.com/RootRecord-Software-Solutions/RootRecord-Library/blob/main/Documentation/00-architecture/Pacific-Domain-Import-Playbook-2026-09-28.md) |
| Work orders | [Work-Orders/](https://github.com/RootRecord-Software-Solutions/RootRecord-Library/tree/main/Documentation/06-development/Work-Orders) |

---

## Migration status — single list

**Legend**

| Status | Meaning |
| --- | --- |
| **MIGRATED** | Superseded on G3; `MIGRATED.md` present; **do not run** |
| **LIVE via G2→G3** | Capability production on Pacific came from G2/desk path, not a bulk G1 packet import |
| **NOT MIGRATED** | Still archive only; may be recovered later under the rules above |
| **ARCHIVE-ONLY** | Never import into G3 runtime git |

### MIGRATED (G1 → org Pacific Automations)

| G1 packet | Status | Production link |
| --- | --- | --- |
| [`hybrid-night-poller/`](./hybrid-night-poller/) | **MIGRATED** 2026-09-28 · [MIGRATED.md](./hybrid-night-poller/MIGRATED.md) | [Automations — `rootserver_poller.py` + stack](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/Automations) |
| [`heartbeat/`](./heartbeat/) | **MIGRATED** 2026-09-28 · [MIGRATED.md](./heartbeat/MIGRATED.md) | [Automations — `jobs.py` builtin `heartbeat`](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/blob/main/Automations/scripts/jobs.py) |
| [`net-gate/`](./net-gate/) | **MIGRATED** 2026-09-28 · [MIGRATED.md](./net-gate/MIGRATED.md) | [Automations — `poller/internet_gate.py`](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/blob/main/Automations/scripts/poller/internet_gate.py) |

### LIVE via G2→G3 (not a G1 packet promotion)

These capabilities run on org Pacific. G1 cousins below remain **NOT MIGRATED** as packets (diff-only recovery later if needed).

| Capability | Production link | G1 cousins still archive |
| --- | --- | --- |
| Automations engine (poller, jobs, stack) | [Automations/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/Automations) | `hybrid-night-poller`, `heartbeat`, `net-gate` → **MIGRATED**; `scheduler-clock` still NOT MIGRATED |
| Energy (EcoFlow read path) | [Energy/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/Energy) | `energy/` |
| System (host sample) | [System/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/System) | `host-metrics`, `system-perf`, `uptime-log`, `log-cleanup` |
| Communications / tunnel / network globe (partial) | [Communications/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/Communications) | `communications/`, `network-globe/`, `local-data-globe/`, `cloudflare-workers/` |
| Github catalog (partial) | [Github/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/Github) | `git-auto-push/` |

### NOT MIGRATED — G3-core candidates (after G2)

| G1 packet | Intended G3 home | Notes |
| --- | --- | --- |
| `energy/` | [Energy/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/Energy) | Diff only after G2 energy (already LIVE) |
| `weather/` | Weather/ | After G2 weather daemon |
| `communications/` | [Communications/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/Communications) | telegram / discord / slack |
| `network-globe/` | [Communications/network/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/Communications) | Globe |
| `local-data-globe/` | Communications/network | Globe cousin |
| `host-metrics/` | [System/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/System) | Align with live System |
| `git-auto-push/` | [Github/](https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server/tree/main/Github) | Compare to G2/G3 github scripts |
| `reports/` | Reports or Automations helpers | After G2 worklog |
| `earthquakes/` | Geology/ | Geology owns quake functions |
| `kilauea/` | Geology/ / Security | Cams / alerts — product split possible |
| `panels-cam/` | Security/ | |
| `system-perf/` | System/ | |
| `log-cleanup/` | Database Logs policy | Not a Pacific Logs domain |
| `uptime-log/` | System/ or Database | |
| `ollama-client/`, `ollama-env/`, `ollama-lifecycle/` | Plumbing or System | G2 `plumbing/` residual first |

### NOT MIGRATED — likely retire / verify only

| G1 packet | Notes |
| --- | --- |
| `scheduler-clock/` | Likely superseded by G3 Automations — verify before any merge |
| `boot/` | Historical boot — Master-Prompt / ops docs, not blind runtime |

### NOT MIGRATED — product / website / Library (not Pacific core)

| G1 packet | Prefer |
| --- | --- |
| `advertising/`, `clients/`, `companions/`, `fern-forest/`, `finance-desk/`, `goals/` | Product repos |
| `minecraft/`, `player-economy/`, `rcon/`, `rootmc-android/` | RootMC / product |
| `live-data-pages/`, `public-edge/`, `public-chat/`, `public-finance/`, `public-health/` | Website / public surface |
| `site-backgrounds/`, `site-ops/`, `vercel-builds/`, `websites/` | Website |
| `persona/`, `governance/`, `ecosystem-index/`, `topics/`, `skill-creator/`, `council/` | [RootRecord-Library](https://github.com/RootRecord-Software-Solutions/RootRecord-Library) / Agent Context |
| `stripe-poll/`, `subscribers/`, `product-prices/`, `pantry/` | Product / billing |

### NOT MIGRATED — review / operator pick

`account-import`, `api`, `cloudflare-workers`, `code-review`, `core-ops-install`, `day-board-boot`, `desk-data-reader`, `ensure-ava-runtime`, `feature-toggles`, `fs-index`, `hourly-clip-reports`, `idle-stop`, `inbox`, `inbox-drain`, `kokoro`, `launch`, `live-directories`, `load-categories`, `look`, `merged-morning`, `model-pick`, `morning-boot-replay`, `mp4-converter`, `obs-studio`, `ops-banner`, `overnight-relay`, `people`, `python-drop-runner`, `reply-feedback`, `research-oa`, `root-record-registry`, `state`, `sunrise-restore`, `synth`, `users`

### ARCHIVE-ONLY — never bulk into G3 runtime

| G1 packet | Reason |
| --- | --- |
| `origin/` | ~4k paths — external or Library archive decision only |
| `origin-session/` | Session dump |
| `ecosystem-history/` | Large history |
| `history/`, `holding/`, `remaining-tasks/`, `recycle-origin/` | Ops archive |
| `database/`, `mysql/` | Policy → [RootRecord-Database](https://github.com/RootRecord-Software-Solutions/RootRecord-Database) — not G3 code dump |

---

## Summary counts (approx.)

| Bucket | Count |
| --- | --- |
| **MIGRATED** (G1 packet + MIGRATED.md) | **3** |
| LIVE capability on G3 (via G2→G3, not G1 promotion) | Automations, Energy, System, partial Comms/Github |
| NOT MIGRATED (all other tops) | Remainder of ~95 tops |
| ARCHIVE-ONLY hard stops | `origin/`, `ecosystem-history/`, … |

---

## Related production links (bookmark)

| Role | Link |
| --- | --- |
| Org | https://github.com/RootRecord-Software-Solutions |
| Pacific runtime | https://github.com/RootRecord-Software-Solutions/RootRecord-Pacific-Solar-Server |
| Library | https://github.com/RootRecord-Software-Solutions/RootRecord-Library |
| Database | https://github.com/RootRecord-Software-Solutions/RootRecord-Database |
| G2 intermediate (residual skills) | https://github.com/rootrecordsoftwaresolutions/Solar-Pacific-RootRecord-Server |

---

*README created 2026-09-28 HST. Update the status tables whenever a packet gains `MIGRATED.md` or a domain import completes.*
