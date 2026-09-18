---
name: panels-cam
description: >-
  Night Owl Rear Shed panels camera. Use when asked to show the panels,
  solar panels cam, or rear shed still. Powers River 2 Pro car 12V if the
  camera is dark, grabs RTSP, then turns car DC off unless drives hold it.
---

# panels-cam

Live still of the **Rear Shed** solar panels (Night Owl DVR channel 1).

Camera rides **River 2 Pro car / 12V DC**. The DVR stays on the LAN. Never toggle AC. Starlink stays on Delta AC.

## Show me the panels

```bash
~/.ollama/skills/origin/.venv/bin/python \
  ~/.ollama/skills/panels-cam/scripts/panels_grab.py --show
```

Telegram / council: say **“show me the panels”** (Ava/Bruce). Owner or open exec skill posts the JPEG.

**Manual always-on:** if River car 12V was already on, we leave it on after the grab (same 12V as external drives).

Carly’s **energy desk** (`energy-report`) reuses the latest still — no second power cycle.

## 15-minute auto

Scheduler job `panels-cam` every 15 minutes (night-sleep gated with other Ava crons): car on → grab → save → car off (unless drive session holds car DC).

```bash
.../panels_grab.py --auto on|off
.../panels_grab.py --tick
```

State: `ecoflow-ble-poller/store/state/panels-cam.json`  
Frames: `panels-cam/store/frames/`  
Connection: `panels-cam/store/CONNECTION.json` (also under `look/store/camera-dvr/`)

## Offline

If the frame stays black, turn River car DC on (camera power), wait, grab again. If still dark, check the BNC / corrosion — not a software path.
