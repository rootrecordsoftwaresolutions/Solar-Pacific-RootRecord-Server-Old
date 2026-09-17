# Migrate `admob-eod`

Status: **moved**.

From `apps/core/crons/on_time/admob_report.py` to `~/.ollama/skills/admob-eod/scripts/job.py`.
Ava-Core cron is a shim.

Do not restore the old body.
Processor `admob.py` from `apps/core/services/admob.py`.

