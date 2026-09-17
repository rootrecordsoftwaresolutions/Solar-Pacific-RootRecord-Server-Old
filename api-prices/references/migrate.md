# Migrate `api-prices`

Status: **moved**.

From `apps/core/crons/on_time/api_prices.py` to `~/.ollama/skills/api-prices/scripts/job.py`.
Ava-Core cron is a shim.

Do not restore the old body.
Processor `api_ledger.py` from `apps/core/services/api_ledger.py`.

