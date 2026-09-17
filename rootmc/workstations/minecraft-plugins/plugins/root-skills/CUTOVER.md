# mcMMO → root-skills cutover

Do **not** delete `mcMMO.jar` / `plugins/mcMMO/` from handoffs until this sequence succeeds on the target host.

## Sequence (shared Towny MySQL)

1. **Stop mcMMO** on Claims / Towny / Test (disable jar or stop the server plugins).
2. **Migrate** — run root-skills admin migrate (dry-run first, then apply) so `mcmmo_*` levels/XP merge into `root_skills_users` + `root_skills_skills`.
3. **Start root-skills** — deploy `root-skills.jar` + configs; confirm `/skills` shows migrated power.
4. **RootMC** — ensure `skills.enabled: true` and `skills.table-prefix: root_skills_` in `rootmc.yml` (defaults). Cloud sync still posts to `/api/realm/minecraft/mcmmo/sync` with the same payload shape.
5. **Verify** — PAPI `%rootmc_skills_power%`, holograms, Android leaderboards; `%rootmc_mcmmo_power%` remains a deprecated alias.
6. **Then** remove mcMMO jars/folders from handoffs once all hosts are verified.

Rollback: re-enable mcMMO, set `skills.enabled: false` (or leave tables empty so RootMC falls back to `mcmmo_`), restart.
