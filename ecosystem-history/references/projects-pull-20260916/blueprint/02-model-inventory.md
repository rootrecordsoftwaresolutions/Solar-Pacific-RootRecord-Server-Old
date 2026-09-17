# Model Inventory

All models run through Ollama. Total footprint across every model below is
roughly ~46GB (well under the 100GB budget given in the original session).
Pull everything in one pass:

```bash
# ==============================================================================
# ROOTRECORD MODEL LIBRARY — full pull list (~46 GB)
# Optimized for AMD Ryzen AI 5 430 (4 threads / 16GB RAM / 8MB L3 cache)
# ==============================================================================

# --- Coding & repo-level reasoning ---
ollama pull qwen2.5-coder:7b-instruct-q4_K_M    # main codebase logic, multi-file generation
ollama pull deepseek-coder:7b-instruct-q4_K_M   # refactor-heavy alternative coder
ollama pull deepseek-coder:1.5b-instruct-q8_0   # sub-second inline completions
ollama pull granite3-code:2b-instruct           # lightweight background code analysis

# --- Routing / orchestration / tool-calling ---
ollama pull llama3.1:8b-instruct-q4_K_M         # architect / routing agent, 128k context
ollama pull mistral:7b-instruct-v0.3-q4_K_M     # alternate strict-instruction router
ollama pull qwen2.5:7b-instruct-q4_K_M          # elite JSON/tool-call formatting

# --- Persona / public-facing chat ---
ollama pull gemma2:9b-instruct-q4_K_M           # tone & personality — used for Ava/Bruce/Carly
ollama pull phi3.5:3.8b-instruct-q4_K_M         # tiny, strong multi-step logic router
ollama pull llama3.2:3b-instruct-q4_K_M         # fast multi-user streaming
ollama pull qwen2.5:1.5b-instruct-q8_0          # fastest multi-user streaming

# --- Embeddings ---
ollama pull nomic-embed-text                    # vectorizes repo tree for local RAG

ollama list   # verify
```

## Role assignment

| Model | Role | Used in |
|---|---|---|
| `qwen2.5-coder:7b-instruct-q4_K_M` | Primary code generation | `reference-code/pipeline.py` |
| `deepseek-coder:7b-instruct-q4_K_M` | Alt. refactor-focused coder | — |
| `deepseek-coder:1.5b-instruct-q8_0` | Inline autocomplete (VS Code Continue) | `07-services/vscode-integration.md` |
| `granite3-code:2b-instruct` | Background codebase summarization | — |
| `llama3.1:8b-instruct-q4_K_M` | Architect / tree-routing agent | `reference-code/pipeline.py` |
| `mistral:7b-instruct-v0.3-q4_K_M` | Alt. router, strict system-prompt adherence | — |
| `qwen2.5:7b-instruct-q4_K_M` | Intent router / skill selector (JSON output) | `reference-code/server.py`, agent skill router |
| `gemma2:9b-instruct-q4_K_M` | Persona voice (Ava / Bruce / Carly) | `04-agent-personas/`, `reference-code/debate.py` |
| `phi3.5:3.8b-instruct-q4_K_M` | Background multi-step routing | — |
| `llama3.2:3b-instruct-q4_K_M` / `qwen2.5:1.5b-instruct-q8_0` | High-concurrency public chat | Open WebUI persona bots |
| `nomic-embed-text` | Embeddings for local RAG over the repo tree | `reference-code/pipeline.py` |

## Hardware note

Set CPU threads to 4 in whatever runner is active — this matches the L3 cache
layout described in `01-hardware-and-environment.md`. Never load more than one
model at a time; let Ollama's automatic swap handle transitions between
pipeline stages instead of trying to keep multiple 7–9B models resident.
