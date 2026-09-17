# Migrate `inbox-drain`

Status: **moved**.

From `apps/core/crons/always_on/inbox_drain.py` to `~/.ollama/skills/inbox-drain/scripts/job.py`.
Ava-Core cron is a shim.

Do not restore the old body.
Processor `offline_inbox.py` from `apps/core/services/offline_inbox.py`.

