# Workstations

Plugin and app source trees. On AVA-CORE they stay on the USB and are joined into
`C:\Users\rootr\ava` (same idea as Media). Runtime Paper worlds and MySQL datadirs
do **not** belong on C:.

Live joins:

| Live path | Target |
|-----------|--------|
| `C:\Users\rootr\ava\workstations` | `E:\ava\workstations` |
| `C:\Users\rootr\ava\plugins` | `minecraft-plugins/plugins` |

Product / Capacitor-web apps (junctions under `C:\Users\rootr\ava\apps`, files stay on E:):

| `apps\` name | Source |
|--------------|--------|
| `kilauea-alerts` | `workstations/android/kilauea-alerts` |
| `rootmc-android` | `workstations/android/rootmc` → `api.rootmc.net` |
| `solana-rootrecord-site` | `workstations/solana-rootrecord-site` |
| `weather-manager-web` | pre-August `apps/weather-manager-web` (public API held) |
| `business-manager-web` | pre-August `apps/business-manager-web` (public API held) |
| `kilauea-alerts-web` | pre-August `apps/kilauea-alerts-web` |
| `account-hub-web` | pre-August `apps/account-hub-web` |
| `token-manager-web` | pre-August `apps/token-manager-web` |
| `root-farms-web` | pre-August `apps/root-farms-web` |
| `root-farms-mobile-web` | pre-August `apps/root-farms-mobile-web` |
| `root-goals-web` | pre-August `apps/root-goals-web` |
| `visiting-hawaii-web` | pre-August `apps/visiting-hawaii-web` |
| `realm-web` | pre-August `apps/realm-web` (RootMC redirect source; do not deploy `rootmc-api`) |

Pre-August `apps/shared` is three JS helpers, not joined as an app. `rootmc-web` / `rootmc-api` stay under `workstations` only.

| Folder | What |
|--------|------|
| `android/kilauea-alerts/` | Kilauea Alerts (`com.rootrecord.kilauea`) → `api.rootrecord.online` until rootrecord.cloud product APIs |
| `android/rootmc/` | RootMC app (`com.rootrecord.rootmc`) → `api.rootmc.net` |
| `android/builds/` | Release APKs |
| `minecraft-plugins/` | Paper plugin Gradle monorepo → `api.rootmc.net`. Gradle on JDK 17, bytecode JDK 25. |
| `minecraft-test/` | Local Paper tree (worlds stay on E:) |
| `cloudflare/` | Previous CF worker sources (live Workers are `C:\Users\rootr\ava\packages\workers`) |
| `rootmc-web/` | RootMC web + `rootmc-api` (Minecraft-only; do not deploy unless a Minecraft change is verified) |
| `rootmc-scripts/` | Deploy / build scripts |
| `obs/` | OBS overlay HTML / lua |
| `solana-rootrecord-site/` | Solana public site |
| `rootmc-docs/` | Plugin/server docs |

RootMC API stays `api.rootmc.net`. Ava origin is `127.0.0.1:8787` only. Product weather/business APIs stay held.

Re-join on Windows (idempotent, no copy):

```powershell
powershell -File C:\Users\rootr\ava\scripts\import_workstations.ps1
```
