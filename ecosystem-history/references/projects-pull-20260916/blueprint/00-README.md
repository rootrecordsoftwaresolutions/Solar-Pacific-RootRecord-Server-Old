# RootRecord Ecosystem — Build Blueprint

This is the organized, de-duplicated version of the original planning transcript
(`context_for_AI_pipelines_and_upgraded_self_hosted_coder_and_reasoning_agents.txt`).
That file was a long back-and-forth with an AI where ideas evolved and were
corrected multiple times (agent names, timelines, permissions). This blueprint
keeps only the **final, corrected** version of each decision so a build agent
isn't working from superseded drafts.

Read `01` → `08` in order for full context. Each file is self-contained enough
to reference on its own once you've read `01`–`03`.

## Index

| File | Contents |
|---|---|
| `01-hardware-and-environment.md` | Host specs, naming rules, directory layout |
| `02-model-inventory.md` | Every model to pull, and its role |
| `03-architecture-overview.md` | How all the pieces connect |
| `04-agent-personas/` | Ava Ivy, Bruce Monitor, Carly Mal — full lore + system prompts |
| `05-workflows/` | Debate engine, proposal/execution gate, backups, new-project handling |
| `06-permissions-and-safety/` | Filesystem sandboxing, DB permissions, mining/AI exclusivity |
| `07-services/` | FastAPI token server, VS Code hookup, Open WebUI dashboard |
| `08-future-roadmap.md` | Later-stage ideas — **flagged as needing scope decisions** |
| `09-model-archive-management.md` | What's actually installed vs. the original recommended list, and how oversized models are archived rather than deleted |
| `reference-code/` | Consolidated, de-duplicated source files (one canonical version each) |

## Open design questions (resolve before a build agent starts)

The original session stated several goals as one-liners without ever turning
them into a buildable spec. Recommend deciding these explicitly first —
they're each called out again in context in the relevant file below:

1. **Autonomous wallet/spending access** (`08-future-roadmap.md`). The transcript
   says the three agents should eventually get CLI access to create wallets and
   a shared Litecoin wallet for "roadmap functions requiring funds," with no
   spending limits, approval flow, or custody model defined. This is the single
   highest-risk item in the whole blueprint — worth designing the approval
   mechanism *before* any wallet code exists, not after.
2. **Mining / local-AI mutual exclusion** (`06-permissions-and-safety/mining-ai-exclusivity.md`).
   The rule ("never let AI run while mining runs, or the session freezes and logs
   out") is stated but no enforcement mechanism was ever specified. Needs a
   concrete lock (e.g., a PID/lock file both the mining launcher and the Ollama
   launcher check) rather than being a convention someone has to remember.
3. **"Desires" / Roadmap Contemplation Mode** (`08-future-roadmap.md`). Agents are
   meant to generate their own goals when idle. No bounds were set on what a
   "desire" can propose or how it enters the proposal/execution gate. Recommend
   routing anything a desire produces through the same human-approval gate as
   normal proposals (`05-workflows/proposal-execution-gate.md`), with no
   separate, looser path.
4. **"Never mention the word laptop"** is a lore/branding rule for how the
   agents talk about the host, not a technical constraint — kept as-is in
   `01-hardware-and-environment.md`, just noting it so it isn't mistaken for
   something functional.

Everything else in the transcript was specified concretely enough to build
directly from the linked files.
