# Architecture Overview

## System layers

```
                        ┌──────────────────────────┐
                        │      Open WebUI (8080)    │  human-facing dashboard,
                        │  persona chat, admin auth  │  multi-user login
                        └────────────┬──────────────┘
                                     │  OpenAI-compatible API
                        ┌────────────▼──────────────┐
                        │   FastAPI Gateway (8000)   │  token-tracked, streaming,
                        │  server.py — see 07-       │  per-user SQLite auth
                        │  services/                 │
                        └────────────┬──────────────┘
                                     │
             ┌───────────────────────┼───────────────────────┐
             │                       │                        │
   ┌─────────▼─────────┐  ┌──────────▼──────────┐  ┌──────────▼──────────┐
   │   Ollama runtime    │  │  Debate Engine       │  │  Web-search skill    │
   │  OMP_NUM_THREADS=4  │  │  (Ava/Bruce/Carly)   │  │  (DuckDuckGo, no key)│
   │  serial model swap  │  │  see 05-workflows/    │  │                      │
   └──────────────────────┘  └──────────┬───────────┘  └──────────────────────┘
                                         │
                              ┌──────────▼───────────┐
                              │ Proposal/Execution     │  human-in-the-loop
                              │ Gate (zip staging)     │  gate before anything
                              │ see 05-workflows/       │  touches real code
                              └──────────┬───────────┘
                                         │
                              ┌──────────▼───────────┐
                              │   /mnt/Projects        │  active codebase +
                              │   (mutable workspace)  │  shared SQLite DB
                              └────────────────────────┘

   VS Code (Continue extension) → talks to FastAPI Gateway directly (07-services/vscode-integration.md)
```

## Component summary

- **Ollama** — local model runtime. Always thread-locked (see `01`). Every
  other component talks to it via `http://localhost:11434/v1`
  (OpenAI-compatible).
- **FastAPI Gateway (`server.py`)** — the one production entry point. Handles:
  auth via API key, per-user token tallies in SQLite, streaming responses,
  chat-log auditing, and exposes the web-search skill. Full spec in
  `07-services/fastapi-token-server.md`.
- **Debate Engine (`debate.py`)** — the three named agents (Ava Ivy, Bruce
  Monitor, Carly Mal) run a sequential round-robin discussion over a topic,
  reading/writing a shared "blackboard" table, voting on consensus, and
  producing a `report.md`. Full spec in `05-workflows/debate-engine.md`.
- **Proposal/Execution Gate** — anything the agents want to change in real
  project files gets staged as a dated zip (`Proposal-MMDDYYYY-XXX.zip` /
  `Patch-MMDDYYYY-XXX.zip`) and only applied when a human explicitly approves
  it by name. Nothing auto-commits. Full spec in
  `05-workflows/proposal-execution-gate.md`.
- **Open WebUI** — browser dashboard for humans to chat with named persona
  bots, pointed at the FastAPI gateway instead of raw Ollama. Setup in
  `07-services/open-webui-dashboard.md`.
- **VS Code / Continue** — IDE-side hookup, either straight to Ollama for
  autocomplete or through the custom FastAPI gateway for the full
  tool-routing pipeline. Setup in `07-services/vscode-integration.md`.

## Data flow: a typical request

1. User message arrives at the FastAPI gateway (from Open WebUI, VS Code, or
   a direct API call) with an API key.
2. Gateway authenticates the key against SQLite (`/mnt/Projects/api_users.db`),
   checks tier/token limits.
3. A router model (`qwen2.5:7b-instruct`) decides: direct chat, web search, or
   codebase search — and returns strict JSON naming the tool + parameter.
4. The matching skill fires (DuckDuckGo search, or a vector query against the
   locally indexed repo tree via `nomic-embed-text` + Chroma/SQLite).
5. The persona model (`gemma2:9b-instruct`) composes the final reply using
   that injected context, streamed back token-by-token.
6. Token usage and the full exchange are logged to SQLite for that user.

For multi-agent design/debate tasks specifically, step 3–5 are replaced by the
debate engine loop — see `05-workflows/debate-engine.md`.
