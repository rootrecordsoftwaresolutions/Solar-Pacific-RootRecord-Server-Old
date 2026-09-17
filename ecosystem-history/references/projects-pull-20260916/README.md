# Projects disk pull — 2026-09-16

Source mount: `/mnt/Projects` (`/dev/sdb1`, label `Projects`, ext4).

This folder is **history and leftover docs**, not live runtime.

## What was on the disk

| Path | What it was | Disposition |
| --- | --- | --- |
| `Clients/` | Web-dev gigs (nibble.love static) | Live: `clients` skill |
| `Fern Forest/` | Three qPublic PDFs | Live: `fern-forest` skill |
| `FreeLTC/` | Product draft + blueprint zip | Live: `freeltc` skill; blueprint here |
| `RootRecord Core/` | Early Linux staging home (Windows leftover layout) | Docs here; media already in `~/Media`; ollama-bench → `~/Documents/ollama-bench` |
| `RootRecord-Core-Ops-consolidation-20260913.tar.gz` | 552K Core Ops snapshot (201 paths, mostly git + Dev-Desk + 7 Sep report) | **Superseded.** Live tree is `/home/rootrecord/RootRecord/RootRecord-Core-Ops` |
| `.Trash-1000/` | Empty | Drop |

## RootRecord Core vs live

The 2026-09-14 Linux migration matrix on that disk still pointed at
`/home/rootrecord/RootRecord/repos/...` and `/mnt/Projects` as the workspace.
Live layout is `/home/rootrecord/RootRecord/<repo>` (no `repos/` wrapper).
`AGENTS.md` on this OmniBook is the authority, not the Projects copy.

Music and Images already had MOVED notes into `/home/rootrecord/Media`.
ollama-bench was missing from `~/Documents` and was copied there.

Blueprint `00-README.md` is a 2026-09 planning pack (Open WebUI, FastAPI token
server, “never say laptop”). Do not treat it as live. Live inference is Ollama
on Vulkan 840M; FastFlowLM is on-demand brainstorm only.

Model quality report on that disk is a **CPU-only** 11 Sep stress test
(`gpu_percent` ~0). Later live bench is `~/Documents/ollama-bench/` (Vulkan
offload confirmed).

## Not copied into skills

- Anno 1800 game data
- Hardware inventory serials (Documents/AVA-CORE-CONTEXT only)
- Phone tax PDFs (Documents/imports/projects-phone)
- Minecraft server zip/worlds on Desktop → `/mnt/Archives/rootmc-backups-09032026`
- Desktop Albums → `~/Media/public/images/imports/projects-desktop-albums/`
- Blueprint `reference-code/` (superseded by Ava-Core / skills)
