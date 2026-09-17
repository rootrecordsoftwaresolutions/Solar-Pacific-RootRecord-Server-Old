# How RootRecord works today — and how it got here

Hand-maintained. Update this file when live identity, host, public doors, or
power wiring change. Dated file lists stay in CURRENT.md (generated).

Clock for this draft: 2026-09-16 afternoon HST. Sources: live AGENTS.md, skill migrate notes, council-deploy Implemented notes, Media public plans/notes, AVA-CORE-CONTEXT
INDEX/DRIVES copies under Stale Root Reports. No invented watts, SOC, or player counts.

## Live now

- Host is the HP OmniBook 5, Ubuntu, user `rootrecord`. The Dell OptiPlex is dead.
- NPU: FastFlowLM `llama3.2:3b` via enabled `ava-flm.service` (graphical session + 25s, default pmode, `:52625`). Launch does not start a second copy. ~9–11G RSS. Idle-stop unmaps it. Coder/vision stay on Ollama. Chat prefers `:52625`, else GGUF.
- Live tree: `/home/rootrecord/RootRecord/Ava-Core`. Origin is `http://127.0.0.1:8787/` on this PC / LAN.
- Android SDK: `~/.local/opt/android-sdk`. Builders: `~/.local/opt/rootrecord/android-build`. Phone apps in `ava-ops`, `kilauea-alerts`, `rootmc-android`. Mobile web: `rootmc-mobile-web`.
- Media primary: `/home/rootrecord/Media`. Public reports and blog markdown live under `Media/public/`.
- Public doors: rootrecord.cloud and avaivy.cloud. RootMC live game is `play.rootmc.net`. Player currency is Gold, not USD.
- Council: Ava (public wording), Bruce (ops), Carly (security). Telegram group. Public report drafts are council-published.
- EcoFlow: DELTA 2 and RIVER 2 Pro only. Starlink is Delta AC (never switched). 400 W gate owns Delta USB-C into River USB-C. River AC stays on for the laptop. External drives: River 2 Pro car 12V (`ecoflow-river-car` `drive_automation.py`); default off; owner/Cursor/ops can PUT car on; scheduler `drive-automation` every 30 min skips until auto + copy jobs (copy still stub). Night window is 00:00 HST to sunrise; BLE poller owns idle-stop/reboot, not Starlink. Ava scheduler jobs skip while night-mode sleeping is true.
- Topic skills (2026-09-16): public sites are function desks (`avaivy-cloud`, `rootrecord-online`, `alexrs94-site`, `holding`) with Vercel apps under `Ava-Core/sites/<name>/`. Workers are `cloudflare-workers` (`Ava-Core/workers`). Live sqlite/state is the `database` skill store. EcoFlow vendor/quota/sqlite is `ecoflow-ble-poller/store`. Hybrid notebooks are `hybrid-reports/store/Reports`. Ava-Core `data/`, `packages/`, `scripts/`, and `Sites/` are gone. Sibling clones under `~/RootRecord` except Ava-Core are gone.

Do not treat these as live: `C:\Users\rootr\ava`, `/home/ava-core/ava`, OptiPlex LAN, Towny-as-production.

## Evolution (from dated files, condensed)

**Through 2026-07.** Public RootRecord products already existed (Kīlauea, weather, RootMC, sites). Disk from 31 Jul 2026 still has an Ava Ivy implementation-status plan. The desk was not yet this OmniBook Ubuntu tree.

**2026-08.** Dual-world: Windows OmniBook (`C:\Users\rootr\ava`) as the named live Windows tree, Linux OptiPlex (`/home/ava-core/ava`) as the always-on solar box, USB D:/E: as cold archive. Docs from 1–5 Aug cover C-only cutover intent, EcoFlow pack labels, RootMC Gold, Towny not live, GEO/context packs, Grok-then-local reports. Storage plan of 4 Aug said runtime on SSD, archive on HDD, external drive not required to boot. Those SSD/HDD paths (`/home/ava-core/ava`, `/mnt/e`) are **historical**.

**Early 2026-09.** OptiPlex failed. Stale-docs inventory (14 Sep 2026, 23:20 HST) states the OptiPlex is dead (~Aug 2026 burn-out) and that agent-facing docs still pointing at Windows/OptiPlex should not stay on the hot path. AGENTS.md on this tree now names OmniBook Ubuntu as live. Media consolidation moved the primary Media tree to `/home/rootrecord/Media`. EcoFlow BLE became primary (15 Sep plans); River AC is the Starlink night cut. Telegram council plans landed the same day.

**This week.** Topic skills + generated CURRENT.md so the “brain” (Ollama personas + Cursor agents) loads a short map instead of scavenging months of duplicate markdown. That is the point of reducing archive sprawl: the live map is the authority; old trees are evidence, not runtime.

## What the topic maps do for the brain

Ollama still cannot scan disks. The bridge is origin + tools + these skills. If the map is regenerated after a real change, answers stay matched to the 60 HST scheduler jobs and the named modules. If agents read 4 Aug storage plans as live, they lie. NARRATIVE.md + CURRENT.md are the correction.

## Archives — keep vs drop

`/mnt/4tb` is the 3.6T disk (`sda1`, label `4tb`, fstab). 2026-09-16 it was a fake folder on the NVMe; the tree was rsynced onto this disk and the NVMe copy removed. CHECKPOINTS stayed. Coin datadirs and the August dump live here.

`/mnt/4tb` is the 3.6T disk (`sda1`, label `4tb`, fstab). 2026-09-16 it was a fake folder on the NVMe; the tree was rsynced onto this disk and the NVMe copy removed. CHECKPOINTS stayed. Coin datadirs and the August dump live here.

`/mnt/Archives` (sdc NTFS) still holds **Litecoin** datadir, SteamLibrary, and ollama-models. Those stay.

On 2026-09-16 a docs-only pull went to `ecosystem-history/references/archives-pull-20260916/` (Doc-Repo, marketing copy, RootMC change logs, db maps, Topics, Emergency pack notes, MonoRepo git remote). Then `.git`, `.Trash-1000`, `db backup`, and `Pre August` were deleted. `August 2026` and `September` were wiped except NTFS husks Linux cannot unlink (`:` names, Wine dosdevices, one corrupt logs dir). Litecoin / Steam / ollama-models stay.

Same day: `/mnt/Projects` (sdb1) was inventoried, then wiped and reformatted ext4 (label `Projects`, same UUID). Web-dev gigs → `clients`. Fern Forest TMKs → `fern-forest`. FreeLTC draft → `freeltc` (still in development). Core Ops tarball from 13 Sep is smaller than the live Core Ops tree. Early Linux migration notes that named `/mnt/Projects` as the workspace are history; live repos are `/home/rootrecord/RootRecord/<name>`. Pull notes: `ecosystem-history/references/projects-pull-20260916/`.

Live code is Ava-Core + `~/.ollama/skills`. Media is `~/Media`. EcoFlow live store is `ecoflow-ble-poller/store`. Hybrid notebooks are `hybrid-reports/store/Reports`. `~/RootRecord` holds only Ava-Core. `~/Agents`, `~/Media.bak-20260915`, `~/RootRecord Core Ops`, and `~/terminals` were folded into skills on 2026-09-16 and removed.
