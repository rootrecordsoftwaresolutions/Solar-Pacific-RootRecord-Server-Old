---
name: report-readiness
description: >-
  Generate reports when validated data is ready (job report-readiness).
---

# report-readiness

This folder **is** the runtime. Do not invent watts.

## How it fires

- Scheduler `_run("report_readiness")` loads `scripts/job.py`.
- Ava-Core cron file is a 5-line exec shim.
- **Skipped in night sleep**.

Topic index: `reports-voice`.
