---
name: ecoflow-ble-poller
description: >-
  EcoFlow BLE poller (Delta 2 + River 2 Pro). Use when asked how BLE quota,
  night sleep, or ava-ecoflow-ble.service runs. Starlink is Delta AC (never
  switched). 400 W gate owns Delta USB-C.
---

# ecoflow-ble-poller

This folder **is** the poller runtime. Quota JSON, history jsonl, sqlite, and vendor eflib live in `store/`. Do not invent watts or SOC. Do not dump serials.

## How it fires

- User unit `ava-ecoflow-ble.service` (`Restart=always`) while **AVA Console is up**. Launch starts it. Idle-stop / console close stops it.
- Does not keep running after the main terminal is closed. Starlink AC is **Delta 2**, never switched by this stop.

Working directory is `store/` (vendor + state). ExecStart is `scripts/ecoflow_ble_poller.py`.

Topic: `ecoflow-automations`.
