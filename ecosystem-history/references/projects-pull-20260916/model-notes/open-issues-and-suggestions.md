# Open Issues & Suggested Improvements — RootRecord Ecosystem

Context for a build/discovery agent reading this repo. These are flagged
gaps and improvement opportunities identified during stress-testing and
pipeline development, not yet implemented. Read once for awareness; act on
them only if/when explicitly asked to.

## 1. GPU offload is unconfirmed and likely not working

`rocm-smi` polling during a full 23-model, 87-minute stress test showed
`gpu_percent` at effectively 0% (max 6%, average 0.01%) the entire run,
despite `rocminfo` correctly detecting the NPU/GPU (`gfx1153`, Krackan
Point RDNA 3.5). ROCm *seeing* the chip and Ollama *using* it are two
different things — this usually means the installed Ollama binary wasn't
compiled with ROCm backend support.

- Check: `ollama -v` and how it was installed (`apt install ollama` vs. the
  official `curl https://ollama.com/install.sh | sh` script). The distro
  package has historically shipped without ROCm linked in even when ROCm
  libraries are present on the system.
- If GPU offload gets fixed, every timing/speed number collected in prior
  stress tests (`model_stress_results_*.json`) is a CPU-only baseline and
  should be treated as such, not as a ceiling.

## 2. Unresolved design risks flagged in the blueprint's own README

`00-README.md` already calls these out explicitly as needing decisions
before a build agent starts — restating here since they're easy to miss:

- **Autonomous wallet/spending access** (`08-future-roadmap.md`) — agents
  are meant to eventually get CLI wallet access with no spending limit,
  approval flow, or custody model defined. Flagged in the blueprint itself
  as the single highest-risk item in the whole plan. Design the approval
  mechanism before any wallet code exists.
- **Mining/AI mutual exclusion** (`06-permissions-and-safety/mining-ai-exclusivity.md`)
  — the rule ("never run mining and local AI inference at the same time, or
  the session freezes") exists only as a convention someone has to
  remember. No lock file or enforcement mechanism has been built yet.
- **"Desires" / Roadmap Contemplation Mode** (`08-future-roadmap.md`) — no
  bounds defined on what an idle agent's self-generated goal can propose.
  Recommendation already on record: route anything a "desire" produces
  through the same human-approval gate as a normal proposal
  (`05-workflows/proposal-execution-gate.md`), no separate looser path.

## 3. Discovery pipeline (`discovery_pipeline.py`) — two efficiency gaps

Not bugs, the script works correctly as-is — but two things will start to
matter once it's run repeatedly against the same repo across a work
session:

- **No embedding cache.** Every run re-embeds every eligible file from
  scratch, even if nothing changed since the last run. A cache keyed on
  file path + mtime (or content hash) would skip re-embedding unchanged
  files, which matters a lot on a repo you're iterating on all day.
- **Embeddings are requested one file at a time.** Each file is a separate
  `POST /api/embeddings` call. Ollama's `/api/embed` endpoint supports
  batched input in a single request — worth switching to once running
  against repos with more than a couple dozen files, since per-file serial
  calls will start to dominate total runtime as repo size grows.
