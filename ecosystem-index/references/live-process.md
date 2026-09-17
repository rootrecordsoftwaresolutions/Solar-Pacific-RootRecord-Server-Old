# Live process — OmniBook AVA-CORE

HST. Operator desk. No invented watts, SOC, or player counts. No `.env` in chat.

## What is live

The HP OmniBook 5 (`RootRecord`, user `rootrecord`) is the desk. Origin is `http://127.0.0.1:8787/` (this PC / LAN). The Dell OptiPlex is dead. Do not use `C:\Users\rootr\ava` or `/home/ava-core/ava`.

Public copy still talks to our API, not to the LLM browsing the internet. Inference stays on this box unless we explicitly add cloud.

## Function desks

Each runner lives in `~/.ollama/skills/<fn>/scripts/`. Cursor `.cursor/skills` is a symlink to that tree. Topic folders (`ecosystem-index`, `boot-idle-origin`, `council-telegram`, …) group desks. They are not a second copy of the code.

Ava-Core `scripts/*.sh` and `origin/ns/apps/` are **shims**. They `exec` or `runpy` the skill body. Do not restore full runner bodies into Ava-Core.

Never overwrite `skill-creator`. Never copy `.env`, Media, or Core Ops EcoFlow quota JSON into a skill.

After changing scheduler, crons, launch/idle, council, or EcoFlow: `python3 ~/.ollama/skills/ecosystem-index/scripts/refresh-all.py`.

## Origin

FastAPI still loads as `apps.core.main:app`. PYTHONPATH is:

`~/.ollama/skills/origin/ns` then Ava-Core.

Login autostart opens **AVA Console** → `~/.ollama/skills/launch/scripts/launch.sh`. That starts Ollama, origin on `:8787`, and the Ava Ops Bluetooth bridge only if `ava-bt-bridge.service` is not already enabled.

Closing AVA Console runs idle-stop: origin, Ollama, BT bridge, OBS, music, companions. Optional stacks stay off until Ava Ops Settings flips them. Companions do not launch OBS. OBS jobs run only when the toggle is on **and** OBS is already open.

Night sleep (`Ecoflow/state/night-mode.json` `sleeping`) skips Ava scheduler jobs.

`recycle-origin` kills `:8787` only. It does not idle-stop the PC. AVA Console must be looping uvicorn for origin to come back by itself.

## Data

Live sqlite, state, and logs: `~/.ollama/skills/database/store` (`DATA_DIR` / `AVA_DATA_DIR`).

`~/.ava/data` is a symlink to that store for leftover env.

`.env` stays in Ava-Core. Media is `/home/rootrecord/Media` (`AVA_MEDIA_DIR`). EcoFlow quota JSON stays Core Ops.

Feature flags: `store/state/feature-toggles.json` and `GET`/`POST /api/ops/features`.

Do not dump rows, emails, or secrets in chat. Schema-only maps are on `desk-data-reader`.

## No Electron

There is no Ava Electron desktop. Origin `:8787` is the UI. `start-ava-desktop.sh` is a no-op. Core Ops Dev-Desk is a separate optional tree.

## Council (Ava / Bruce / Carly)

Telegram long-poll is `python -m apps.council`. systemd unit `ava-council.service` must have PYTHONPATH `origin/ns` then Ava-Core. Tokens stay in `~/.config/ava-council/secrets.env`. Origin’s Telegram token is a different bot — do not mix getUpdates.

Bruce owns plan files under `~/.config/ava-council/runs/proposals/`. One open daily plan (HST). Rounds append. A new file only after `/done` or a new HST day. Owner `/approve plan-…` is Cursor code. Council publishes public report drafts (`publish reports`). Bruce can zip allowlisted copies (`handoff zip`). No secrets in the zip.

Origin recycle used to post “Coming online” / “Ava Console is up” and dump status files. That is off. Recycle: read the latest chat and answer what is still open. No wake-up greeting. If jobs are already queued, keep going. `/resume` and `/holdoff` still use the canned power lines.

## What stays out of skills

- `.env` / credentials
- Media library
- Core Ops `quota/{serial}.json`
- Model blobs under `~/.ollama/models`

## Prove

Origin: `curl -fsS http://127.0.0.1:8787/health`. Tests: Ava-Core `.venv/bin/python -m pytest` with PYTHONPATH `origin/ns` then Ava-Core.
