# Workflow: Sequential Debate Engine (`debate.py`)

A round-robin "panel discussion" pattern: Ava Ivy → Bruce Monitor → Carly Mal,
one at a time, each reading everything the others have said so far before
adding their own turn. Runs entirely sequentially to respect the 16GB RAM /
single-model-at-a-time constraint (see `01-hardware-and-environment.md`).

## State machine requirements

1. **Sequential turns, not parallel.** Only one model call active at any
   instant. Ollama's KV-caching means each subsequent agent's turn is fast
   because earlier tokens are already cached — this is why the pattern is
   RAM-cheap even at 3 agents × several loops.

2. **Context degradation monitoring.** Track cumulative input token payload
   growth every turn (character-count proxy `chars // 4` is fine, or
   `tiktoken`). If the payload approaches the model's practical context
   ceiling, log a stdout warning flagging hardware degradation risk.

3. **Consensus voting.** After loop 3 (and after every loop up to a hard cap
   of 6), run one deterministic (`temperature=0.0`) voting call:
   > "Has a unified architectural consensus been reached? Respond strictly in
   > JSON: `{"consensus": true/false, "reason": "..."}`"
   - `true` → break early, go straight to final synthesis.
   - `false` → run another loop, up to the 6-loop hard cap.

4. **Automated markdown artifact.** On completion, write `report.md` into the
   project directory containing: topic + date metadata, performance metrics
   (loops run, final token size, execution time), the full formatted debate
   transcript (blockquote per agent), and the final synthesized conclusion.

5. **Shared blackboard, not raw chat history in the prompt.** Agents don't
   pass a growing chat array into the model context — they query a shared
   SQLite table (`agent_blackboard`) for the concentrated findings logged by
   teammates so far. This keeps prompts small even across 3–6 loops. See
   `06-permissions-and-safety/database-rbac.md` for who can write to it (Ava
   is read-only).

6. **Expose as an API hook**, e.g. `/v1/pipeline/debate`, so Open WebUI can
   trigger a topic + loop run from the browser and stream the resulting
   consensus back.

## Model assignment

- All three personas run on `gemma2:9b-instruct-q4_K_M` (see
  `02-model-inventory.md`), with per-agent temperature set per
  `04-agent-personas/`.
- Final synthesis stage runs at `temperature=0.2` for stable consolidation —
  a fourth, neutral "AI project manager" system prompt (not one of the three
  named personas) reconciles the transcript into a definitive conclusion.

## Reference implementation

See `reference-code/debate.py` for a complete, consolidated implementation of
this spec — sequential loop, voting, and markdown report generation.
