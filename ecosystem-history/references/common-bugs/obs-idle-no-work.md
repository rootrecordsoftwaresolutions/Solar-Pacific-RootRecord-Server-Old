# OBS idle / performance

**Ask:** Nothing for OBS should run when OBS is not open.

**Observed (2026-09-05):** `obs64` was **not** running. `AVA_ENABLE_OBS=0`. No OBS overlay/browser source was broadcasting.

**Fix:** `obs-studio` skill `obs_presence.py` — require OBS process + `AVA_ENABLE_OBS`. `broadcast-loop` not registered when `AVA_ENABLE_OBS=0`.

**Check:** With OBS closed, `/obs/audio-stream` returns idle HTML. Open OBS + set `AVA_ENABLE_OBS=1` before expecting rotate/push.
