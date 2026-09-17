# Migrate `ecosystem-history`

Status: **history**. Windows/OptiPlex leftover bodies live here. Not OmniBook autostart. Do not copy `.env` or sqlite dumps.

## 2026-09-16 home-folder cutover

Live code: `~/RootRecord/Ava-Core` + `~/.ollama/skills`. Live Media: `~/Media`.

Folded then deleted:

| Old path | Where it went |
| --- | --- |
| `~/Agents` | `persona/references/ava-ivy-workstation/` |
| `~/Media.bak-20260915` | unique files into `~/Media`; rest was duplicate of live Media |
| `~/RootRecord Core Ops` (symlink) + `RootRecord-Core-Ops` | EcoFlow → `ecoflow-ble-poller/store`; reports → `hybrid-reports/store/Reports`; Dev-Desk → `companions/dev-desk`; READMEs → `references/core-ops-20260916/` |
| `~/terminals` | `references/terminals-20260915/` (EcoFlow smoke logs) |
| Sibling clones under `~/RootRecord/` except Ava-Core | READMEs → `references/sibling-repos-20260916/` |

Ava-Core stays. Public sites stay `Ava-Core/sites/<name>/`. Workers stay `cloudflare-workers`.

Dated Media dump is gone. Live Media is `/home/rootrecord/Media`.
