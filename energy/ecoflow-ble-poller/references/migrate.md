# Migrate `ecoflow-ble-poller`

Status: **moved**. 2026-09-16: vendor, quota JSON, sqlite, history, loads, state live in `store/`.

Poller + `ecoflow_ble_store.py` stay in `scripts/`. Working directory is `store/`.
Do not dump quota/{serial}.json in chat.
