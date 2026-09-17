---
name: origin
description: >-
  Local origin FastAPI on :8787.
---

# origin

This folder **is** the runtime.

Does not hold `.env`. Topic index: `boot-idle-origin`.

Origin boot does not start Stream Director or OBS WebSocket. The audio loop
starts on first queued clip. OBS jobs wait for the Ava Ops obs toggle.
