# Migrate `origin`

Status: **moved**.

From origin skill `scripts/` (FastAPI). Import shims live in `ns/apps/` (PYTHONPATH). `.env` stays in Ava-Core. Live data is the `database` skill `store/` (`DATA_DIR`). Voice dir is the `voice` skill.

Tunnel hostname note: `references/tunnel.yml`.

Do not restore the old body.
