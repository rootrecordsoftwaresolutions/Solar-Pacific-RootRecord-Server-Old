# Gen 2 — retired (became Claims)

**Status:** Gen 2 as a separate stack is **retired**. It evolved into the live **Claims** host.

| Live host | Handoff | API |
|-----------|---------|-----|
| **Towny** | `Server Handoffs/2. RootMC - Towny/` | `api.rootmc.net` (or `api-local.rootmc.net`) |
| **Claims** | `Server Handoffs/1. RootMC - Claims/` | same |

## Do not revive

- `api2.rootmc.net`
- Worker `rootmc-api-g2`
- D1 `rootmc-g2` (`0855eb7d-0a22-4696-ac7b-74070278b66d`)
- Local `gen2-27/` prototype tree (removed)

## Still true

- Claims and Towny keep **separate** `cloud.yml` / `database.yml` / MySQL.
- Agents change live credential values only when the operator explicitly asks.
- FileZilla targets are Claims + Towny handoffs under `Server Handoffs/` only.
- Site paths under `/g2/` may remain as legacy Claims economy URLs; they talk to **`api.rootmc.net`**, not api2.
