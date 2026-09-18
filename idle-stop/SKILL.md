---
name: idle-stop
description: >-
  AVA Console close / Idle desk: stop every processor the desk started.
  Does not power off the PC.
---

# idle-stop

This folder **is** the runtime.

## How it fires

- AVA Console close (launch trap) → this script.
- Ava Ops Idle desk: `POST /api/ops/idle-stop`.

Stops origin, Ollama, FastFlowLM, council, BT bridge, EcoFlow BLE poller, hybrid night poller, auto-push timer, OBS, music, companions, local-edge, cloudflared, xmrig. Drops `ava-console-up` first so user units (`ConditionPathExists`) cannot restart until AVA Console writes the flag again.

Refuses to run when another **live** AVA Console pid owns `ava-console.pid` (unless `IDLE_STOP_OWNER_PID` matches that owner). That stops a dying console’s trap/reaper from killing a newer desk.

If the terminal is closed, this desk is idle. No leftover processors. Starlink AC is not switched here. Last pack state stays.

Topic: `boot-idle-origin`.
