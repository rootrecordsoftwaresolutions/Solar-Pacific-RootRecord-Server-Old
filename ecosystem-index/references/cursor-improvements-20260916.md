# Cursor improvements — 2026-09-16 (skills)

Live work. Owner `/approve` file also lives under `~/.config/ava-council/runs/proposals/`.

## Built in skills

- Storm plot + RAMMB history/forecast + 800 nmi Hawaiʻi gate (`hurricane-tracker`, `hurricane-desk`).
- AVA Console owns processors (`launch`, `idle-stop`, `ConditionPathExists` on `ava-console-up`).
- NPU speak unloads GGUF (`council-telegram` `ollama_client.unload_all`). Warm skip if FastFlowLM is up (`ollama-env`).
- Brainstorm stop drops continue rounds (`queue.drop_brainstorm_continue_jobs`).
- Skill ideas stay drafts in `goals/store/skill-ideas/` until promoted.
- Public finance `billNext` uses the last Stripe subscription label on disk.
- Generator BLE-gone card already in `ecoflow-quota` `desk_card`. Ops banner passes `generatorCard`.

## Not built here (not a skills tree)

- Home Media rsync into `~/Media`.
- Discord archive/wipe, public HTML restore, avaivy.cloud Worker chat.
- Buying DDR5. Litecoin send. xmrig.
