# Migrate `account-import`

Status: **moved**.

From `apps/core/crons/since_last_fire/account_import.py` to `~/.ollama/skills/account-import/scripts/job.py`. `in_order_on_boot/account_import.py` is the same shim.

Do not restore the old body.
