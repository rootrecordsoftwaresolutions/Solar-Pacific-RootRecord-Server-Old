# Root-Essentials Big-Bang Cutover (Single Window)

## Pre-cutover (stage only)
- Build and stage `root-essentials-1.3.0.jar` with existing jars.
- Upload `plugins/RootMC/root-essentials.yml` with live MySQL credentials.
- Confirm LuckPerms groups still grant:
  - `essentials.sethome.multiple.default`
  - `essentials.sethome.multiple.pro`
  - `essentials.sethome.multiple.lifetime`
- Add command access nodes (default group): `rootessentials.balance`, `rootessentials.pay`, `rootessentials.sell`, `rootessentials.home`.

## Cutover window
1. Stop server.
2. Remove/disable EssentialsX jars from `plugins/`.
3. Copy `root-essentials-1.3.0.jar` into `plugins/`.
4. Copy/update `plugins/RootMC/root-essentials.yml`.
5. Start server (plugin creates `root_economy_balances` and `root_homes` tables automatically).

## Smoke tests (must pass before opening server)
- `/balance` returns numeric value.
- `/mint hand` converts gold items → G (nugget ¹⁄₉, ingot 1, block 9).
- `/sell` uses chest-shop rolling average when RootMC is online.
- `/baltop` lists top balances from MySQL.
- `/pay <player> <amount>` transfers atomically and blocks self/negative.
- `/paytoggle` blocks `/pay` to a player who opted out.
- `/eco give|take|set|reset` (admin) updates `root_economy_balances` only — no fake mint.
- `/sell hand`, `/sell all`, `/sell blocks` update balances.
- `/spawn`, `/tpa`/`/tpaccept`, `/back`, `/msg`, `/mail`, `/kit starter`
- `/kick`, `/ban`, `/mute`, `/vanish` (admin smoke)
- `/sethome`, `/home`, `/delhome`, `/homes` work with LP home limits:
  - default=3, pro=5, lifetime=8
- `rootmc-shops` purchase path still works:
  - `/buy <item>` quote
  - `/buy confirm ...` withdraws buyer, deposits seller

## Rollback
- Stop server, remove `root-essentials` jar, restore EssentialsX jars/config, restart.
