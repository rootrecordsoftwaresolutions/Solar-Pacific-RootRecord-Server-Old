---
name: public-health
description: >-
  Periodic check that status boards and Root Record Radio stay reachable on the
  public web and local origin. Alerts the council group and wakes radio when
  AVA Console is up.
---

# public-health

Every 5 minutes (scheduler) probes:

- `rootrecord.cloud` / `avaivy.cloud` / `rootrecord.online` status HTML + `/api/status`
- Root Record Radio page + `/api/radio/now` on cloud
- Origin tunnel radio page + `/api/radio/status`
- Local origin `:8787` status/radio when AVA Console is up

When the console is up and local `:8787/health` fails, heal runs `recycle-origin`
(launch restarts uvicorn) and skips tunnel pile-on probes until the desk answers
again. That is the usual cause of a blank public hang.

`/api/radio/status` on cloud stays soft until the edge Workers are redeployed
with the updated public path whitelist. Local CF tokens today only reach the
legacy RootRecord accounts — Workers live on account `d2daf263…` and need a
scoped token (or `wrangler login` on that account) before deploy works.

While the console is up, heal also restarts the API tunnel if
`origin.avaivy.cloud/health` is dark. `recycle-origin` only kills listeners on
`:8787` so it no longer tears down `cloudflared`.

Failures alert the council group (30‑minute cooldown). While the console is up,
posts a gentle `/api/radio/wake` (and sticky on-air if armed). Does not start
origin when the console is closed.

```bash
~/.ollama/skills/origin/.venv/bin/python \
  ~/.ollama/skills/public-health/scripts/public_health.py --json
```

`--no-alert` / `--no-heal` / `--json` available.
