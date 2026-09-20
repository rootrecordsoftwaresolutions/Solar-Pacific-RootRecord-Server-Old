# Migrate `adsense-eod`

Status: **moved**.

From `apps/core/crons/on_time/adsense_report.py` to `~/.ollama/skills/adsense-eod/scripts/job.py`.
Ava-Core cron is a shim.

Do not restore the old body.
Processor `adsense.py` from `apps/core/services/adsense.py`.

