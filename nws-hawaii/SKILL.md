---
name: nws-hawaii
description: >-
  NWS Hawaiʻi by-county hazard poll (job nws-hawaii-counties). Use when asked
  how county alerts are fetched or spoken.
---

# nws-hawaii

This folder **is** the runtime. Do not invent alerts.

## How it fires

- Scheduler job id `nws-hawaii-counties`, every **15 minutes** HST.
- Still runs in night sleep.
- `apps.core.services.nws_hawaii` is a 5-line exec of `scripts/nws_hawaii.py`.
- Speaks when the product hash changes (or first boot). No Grok.

State: `state/store/nws-hawaii.json`. Telegram only on product-hash change after a successful announce. Quiet spoken has no wall-clock. Dated markdown only when the product changed. Current copy under Media reports.

Topic index: `weather-kilauea`.
