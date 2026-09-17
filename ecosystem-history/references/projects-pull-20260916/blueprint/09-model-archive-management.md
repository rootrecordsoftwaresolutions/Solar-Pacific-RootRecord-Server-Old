# Model Archive Management

The model list in `02-model-inventory.md` was the *recommended* set from the
original planning session. In practice, a much larger set ended up installed
(sourced from a separate Google search, not this blueprint) — including
several models too large for the documented 16GB RAM (`01-hardware-and-environment.md`).

Rather than delete the oversized models, they're archived to
`/mnt/Archives/ollama-models` — kept on disk, out of Ollama's active model
store, restorable later (e.g. after a RAM upgrade).

## Why this isn't just "move the files"

Ollama stores models as content-addressed blobs
(`<models_dir>/blobs/sha256-<hash>`) referenced by small manifest files
(`<models_dir>/manifests/registry.ollama.ai/<namespace>/<name>/<tag>`).
**Blobs are deduplicated across tags** — e.g. `llama3.1:8b` and
`llama3.1:8b-instruct-q4_K_M` in this install point at the exact same blob.
Moving a model's files naively risks moving a blob another kept model still
depends on. The archive script resolves this by checking, per blob, whether
any manifest *outside* the archived set still references it before moving.

## Archived (too large for 16GB RAM)

| Model | Size |
|---|---|
| `llama3.1:70b` | 42 GB |
| `qwen2.5-coder:32b` | 19 GB |
| `qwen2.5:32b` | 19 GB |
| `command-r:35b` | 18 GB |
| `qwen3-coder:30b` | 18 GB |
| `gemma4:26b` | 18 GB |
| `VladimirGav/gemma4-26b-16GB-VRAM:latest` | 14 GB |

**Total archived: ~148 GB.**

## Kept active but tight (not archived — flagged for awareness)

These load (9–9.6GB), but leave only 2–3GB of RAM for the OS, Ollama
overhead, and context window — expect swapping or instability under real use,
not an outright failure to load:

`qwen2.5:14b`, `qwen2.5-coder:14b`, `phi4:14b`, `starcoder2:15b`,
`deepseek-coder-v2:16b`, `gemma4:e4b`

## Scripts

- `reference-code/archive_oversized_models.sh` — moves the 7 oversized
  models' manifests + exclusively-owned blobs to `/mnt/Archives/ollama-models`.
  Stops Ollama before moving, restarts after.
- `reference-code/restore_archived_models.sh` — reverses the above. Restores
  everything by default, or pass one or more relative manifest paths (e.g.
  `library/qwen2.5/32b`) to restore just specific models.

Both scripts auto-detect the models directory (checking `$OLLAMA_MODELS`,
`/home/rootrecord/llama.cpp/models`, `/home/rootrecord/llama.cpp`, and
`~/.ollama/models` in that order), and fall back to a filesystem search if
none of those match. Run them directly on RootRecord — they need local
filesystem and `ollama` CLI access this session doesn't have.
