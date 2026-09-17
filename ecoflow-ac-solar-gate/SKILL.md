---
name: ecoflow-ac-solar-gate
description: >-
  Delta 2 USB-C vs total PV gate (400 W). Use when asked how Delta USB is
  switched to feed River. Never toggles Delta AC (Starlink) or River AC.
---

# ecoflow-ac-solar-gate

This folder **is** the gate runtime. Starlink is Delta AC — this gate never toggles AC. It switches Delta USB-C only.

Called after `ecoflow-quota` (`run_after_quota`). Ava-Core `services/ecoflow_ac_solar_gate.py` execs `scripts/ecoflow_ac_solar_gate.py`.

State json stays in Core Ops Ecoflow/state/.

Topic: `ecoflow-automations`.
