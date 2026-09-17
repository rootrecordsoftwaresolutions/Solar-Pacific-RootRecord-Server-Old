# Migrate `database`

Status: **moved**.

Live sqlite from Ava-Core `data/`, `$HOME/.ava/data`, and `$HOME/Ava-Core/Data` now live in `store/`. JSON state is the `state` skill. Runtime logs are the `logs` skill. Leftover `store/state` and `store/logs` dirs are gone. `$HOME/Ava-Core` is gone.

Do not copy `.env`, Media, or Core Ops quota JSON here.
