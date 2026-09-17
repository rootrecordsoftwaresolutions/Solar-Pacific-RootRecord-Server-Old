---
name: core-ops-install
description: >-
  Bootstrap leftover Core Ops runtime dirs (apt + venv + boot.py). Use when
  installing or repairing that store. Does not copy quota JSON or vendor protobuf.
---

# core-ops-install

This folder **is** the runtime. Do not dump `.env` or serials.

`scripts/install.sh` is the bootstrap. `scripts/boot.py` makes `.runtime` dirs and optional venv. `ROOT` is `store/`.

EcoFlow vendor + quota live in `ecoflow-ble-poller/store`. Hybrid notebooks live in `hybrid-reports/store/Reports`.
