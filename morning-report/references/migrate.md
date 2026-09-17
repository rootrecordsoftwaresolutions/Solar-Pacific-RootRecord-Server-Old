# Migrate `morning-report`

Status: **moved**.

Cron body lives in `scripts/job.py`. Ava-Core `crons/on_time/` file is a shim.

Do not restore the old body.
Processor `boot_report.py` from `apps/core/services/boot_report.py`.

