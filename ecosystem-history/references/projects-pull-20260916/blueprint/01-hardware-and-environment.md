# Hardware & Environment

## Identity

- **Ecosystem name:** RootRecord Ecosystem
- **Public-facing host name:** Hawai'i Pacific Solar Root Server
- **Naming rule:** the agents (and any generated docs/UI copy) should never use
  the word "laptop" — this is a branding/lore rule, not a technical constraint.
- **User/host on disk:** `rootrecord@RootRecord`, home directory `/home/rootrecord`

## Physical hardware

- **Device:** HP OmniBook 5 Laptop 16-cg0xxx (CQ1V0UA#ABA)
- **CPU:** AMD Ryzen AI 5 430 — 4 cores / 8 threads, hybrid layout (1× Zen 5
  performance core + 3× Zen 5c efficiency cores)
- **GPU:** Integrated AMD Radeon 840M (4 CUs)
- **Cache:** L1 320KiB, L2 4MiB, L3 8MiB
- **RAM:** 16GB shared/unified
- **BIOS:** AMI F.11

## Performance constraints (drive most of the design decisions below)

- **Thread lock:** Always launch Ollama with `OMP_NUM_THREADS=4` and
  `OLLAMA_NUM_PARALLEL=1`. This mirrors the physical L3 cache layout and
  prevents the workload from bleeding onto slow hyperthreads.
  ```bash
  systemctl stop ollama || true
  OMP_NUM_THREADS=4 OLLAMA_NUM_PARALLEL=1 ollama serve
  ```
- **Serial model execution only.** Never run two local models concurrently —
  16GB RAM is enough for one active model plus tooling, not two. Ollama's
  automatic load/unload handles swapping between pipeline stages; the debate
  engine and pipeline scripts are written around this (see
  `05-workflows/debate-engine.md`).
- **RandomX/mining note (context, not a build task):** this machine also runs
  XMRig for Monero mining. RandomX wants ~2MB of L3 cache per thread, so 4
  threads is the practical ceiling on this CPU's 8MB L3 — this is the same
  reason the LLM stack is also thread-locked to 4. See
  `06-permissions-and-safety/mining-ai-exclusivity.md` for the hard rule that
  mining and local AI inference must never run at the same time.

## Directory layout / trust boundaries

Two zones, strictly separated — full detail and enforcement code in
`06-permissions-and-safety/filesystem-sandboxing.md`:

| Zone | Path | Mutability |
|---|---|---|
| Workspace / active project code, shared SQLite DB | `/mnt/Projects` | Mutable — agents work here freely |
| Core engine, model weights, skills | `/home/rootrecord` | Immutable, with two narrow exceptions (`llama.cpp/*`, `llama.cpp/skills/*`) |

## Python environment

PEP 668 blocks system-wide `pip install` on this machine's Debian-based
Python, so all Python tooling runs inside a dedicated venv:

```bash
python3 -m venv ~/pipeline_env
source ~/pipeline_env/bin/activate
pip install fastapi uvicorn openai chromadb open-webui
```

Re-activate with `source ~/pipeline_env/bin/activate` in any new terminal
session before running pipeline scripts.
