# WatchDog

Local AVA supervision operation for RootRecord Core Ops.

## Ownership

This folder owns the Windows local watchdog cluster:

- `watchdog.py` — health gate, origin supervision, tunnel supervision, and
  official `pythonw -m uvicorn` spawn.
- `net_gate.py` — WAN/power gate, local stop/start behavior, Desk/Ollama
  handling, and power profile updates.
- `operator_purge.py` — sticky operator shutdown, Task Scheduler disable/enable,
  stack cleanup, and controlled resume.

AVA application code remains under `C:\Users\rootr\ava`. The watchdog uses
`AVA_HOME` for that application root and imports its local siblings from this
folder.

## Local flow

1. Task Scheduler runs `watchdog.py` every minute with `pythonw.exe`.
2. WatchDog checks WAN state, `/health`, port ownership, and process age.
3. WatchDog keeps exactly one origin spawner and one cloudflared connector.
4. A healthy origin is left alone.
5. A health-dark mature origin is recycled with cooldown protection.
6. A missing origin is spawned from `ava\.venv\Scripts\pythonw.exe`.
7. `net_gate` stops the local stack after the configured WAN-down interval and
   permits restoration when connectivity returns.
8. `operator_purge` can intentionally keep AVA dark until explicitly cleared.

## Four-operation handoff

| Operation | WatchDog behavior |
|---|---|
| Local AVA Core and Desk | Supervises local origin, Desk, Ollama, music, and power state. |
| Public VPS and Nodes | No VPS behavior yet; future handoff must remain a separate supervisor. |
| Vercel Site Delivery | Does not deploy sites; it only keeps local AVA available to deployment tooling. |
| Cloudflare Edge and Fallback | Keeps the single local tunnel available; Cloudflare fallback owns outage behavior. |

## Safety rules

- Never start a second origin manually.
- Never kill uvicorn by PID count alone; parent/child PIDs can be one origin.
- Never use `powershell.exe` as the scheduled launcher.
- Keep watchdog execution time unlimited (`PT0S`).
- Keep credentials and tunnel tokens outside this folder and Git.
- The future VPS/node supervisor must be added as a separate operation, not mixed
  into this local WatchDog.
