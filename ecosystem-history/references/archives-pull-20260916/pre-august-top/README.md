# RootRecord MonoRepo

Single git repo housing every Root Record property: Android (Capacitor) wrappers, Cloudflare Pages web apps, Cloudflare Workers (APIs + utilities), the marketing site, the public Solana tools site, and a few small bots/docs sub-repos.

- Primary branch: `main`
- Repo root: `C:\Users\rrdeveloper\MonoRepo` (Windows dev path)
- Package managers: `pnpm` for Mobile and product web apps, `npm` for Workers, `npm`+`serve` for the marketing site preview.

## Top-level layout

```
MonoRepo/
├── Mobile/                  Android (Capacitor) wrappers + native Kotlin (kilauea, blocknotes)
├── Web/
│   ├── apps/                Product web apps (Cloudflare Pages)
│   ├── cloudflare/          Cloudflare Workers (APIs, license, solana-tx, etc.)
│   ├── main/                Marketing site (rootrecord-website, Pages + Functions)
│   ├── scripts/             Shared deploy scripts (Pages helpers, analytics injection)
│   └── tools/               Internal tools (e.g. custodial-wallet-manager)
├── scripts/                 Repo automation (`commit-all.ps1`, release helpers)
├── solana-rootrecord-site/  Public Next.js site at solana.rootrecord.info
├── Bots/                    solana-swing-bot / solana-arb-bot / energy tools
├── Doc-Repo/                Long-form internal docs
├── ebooks/                  Static ebook assets
├── commit-all.bat           Stage + commit + push (user runs)
├── cloudflare-update-all.bat        Workers + Pages deploy wrapper (user runs)
├── cloudflare-update-workers.bat    Workers only (user runs)
├── cloudflare-update-pages.bat      Pages only (user runs)
└── cloudflare-delete-primary.bat    Decommission legacy `rootrecord-primary` Worker (user runs, interactive)
```

## Web vs Mobile — important rule

**Web app source is in `Web/apps/<name>-web/`, never in `Mobile/`.**
`Mobile/<app>/` holds only the Android (Capacitor) wrapper: `android/`, `capacitor.config.json` (with `webDir` pointing at `../../Web/apps/<name>-web/build`), and Android build scripts. The web build always runs in `Web/apps/<name>-web/`; `cap sync` pulls that `build/` into the Android project.

## Products and where their code lives

| Product | Web (Pages) | Android (Capacitor / native) | Backend (Worker) |
|---|---|---|---|
| Weather Manager | `Web/apps/weather-manager-web/` → `rootrecord-weather-web` | `Mobile/weather-manager-mobile/` | `Web/cloudflare/rootrecord-api-weather/` |
| Business Manager | `Web/apps/business-manager-web/` → `rootrecord-business-web` | `Mobile/business-manager-app/` | `Web/cloudflare/rootrecord-api-business/` |
| Account Hub | `Web/apps/account-hub-web/` → `rootrecord-account-web` | `Mobile/account-hub-app/` | `Web/cloudflare/rootrecord-api-account/` |
| Token Manager | `Web/apps/token-manager-web/` → `rootrecord-token-web` | `Mobile/token-manager-app/` | `Web/cloudflare/rootrecord-api-token/` |
| Kīlauea Alerts | `Web/apps/kilauea-alerts-web/` → `rootrecord-kilauea-web` (Vite) | `Mobile/kilauea-alerts-android/` (native Kotlin) | `Web/cloudflare/rootrecord-api-kilauea/` |
| RootMC (Minecraft + Android) | [rootmc.net](https://rootmc.net) | `Mobile/rootmc-android/` | `Web/cloudflare/rootmc-api/` + `Web/cloudflare/rootmc-realm-api/` |
| Root Goals | `Web/apps/root-goals-web/` → `rootrecord-goals-web` | `Mobile/root-goals-mobile/` | `Web/cloudflare/rootrecord-api-goals/` |
| Marketing site | `Web/main/` → `rootrecord-website` | — | (Pages Functions in `Web/main/functions/`) |
| Solana tools | `solana-rootrecord-site/` → `solana.rootrecord.info` | — | — |

Other Workers in `Web/cloudflare/`: `rootrecord-license`, `rootrecord-solana-tx`, `rootrecord-app-build`, `shared/` (importable code, e.g. `password-verify.ts`). `rootrecord-primary` is **legacy** — its source has been stripped of weather + duplicate crons but the deployed Worker still serves stale code and a redundant `*/5` NOAA poller until you run `cloudflare-delete-primary.bat` (interactive, destructive). Re-bind any routes that still point at `rootrecord-primary` first. `rr-weather-manager-api` is also **legacy and already deleted from Cloudflare** — its push routes (`push-token`, `push-broadcast`) are merged into every `rootrecord-api-*` shard via `src/push.ts`. The repo source remains for historical reference; it is no longer deployed by `cloudflare-update-workers.bat`.

### Weather / AccuWeather notes (api-weather + api-kilauea only)

- AccuWeather endpoints live ONLY on `rootrecord-api-weather` and `rootrecord-api-kilauea`. Other shards have no `weather.ts`.
- Every external AccuWeather call goes through `accuFetchJson()`, which caches per-URL via the Cloudflare Cache API. Per-path TTLs: daily 5-day = 24h, hourly 72-hour = 1h, current conditions = 30min, alerts = 10min, location lookups = 30d. Cache key omits the API key, so api-weather and api-kilauea share cache fills automatically.
- D1 bundle cache (`weather_data`) is keyed by `grid_key` only — any user fetching `lat,lon` populates the row for every other user (including cross-shard). Default TTL `WEATHER_DATA_TTL_SEC = 3600` (1h); override per Worker with the env var.

## Common tasks

### Commit + push
```
commit-all.bat
commit-all.bat your commit message here
```
Stages **all** changes in the monorepo (`git add -A`), commits (default message `chore: sync workspace` if none given), and pushes to `origin`. Run from repo root.

`scripts\commit-all.ps1` **auto-discovers** project roots (`Web\apps\*`, `Web\tools\*`, `Mobile\*`, all `Web\cloudflare\*` workers, `Web\main`, `Web\scripts`, `solana-rootrecord-site`, `Bots\*`, `Doc-Repo`, `ebooks`, `scripts\`, top-level folders with `package.json`, etc.) and lists which have pending changes before committing. Root-level files (e.g. `cloudflare-update-*.bat`) show as `(repo root)`. Nested `.git` repos, if present, are committed first.

### Deploy Cloudflare (Pages + Workers)
```
cloudflare-update-all.bat       :: Workers, then Pages
cloudflare-update-workers.bat   :: Workers only
cloudflare-update-pages.bat     :: Pages only
```
All three pause on success/failure and write `<script-name>.log` next to themselves. Wrangler auth comes from a `credentials.env` file (gitignored).

### Build a Pages site manually
```
cd Web\apps\weather-manager-web
pnpm install
pnpm run pages:deploy
```

### Build + release an Android app (bumps version, signs, copies to `Mobile\builds\`)
```
Mobile\weather-manager-mobile\bump-and-build-release.bat
Mobile\business-manager-app\bump-and-build-release.bat
Mobile\account-hub-app\bump-and-build-release.bat
Mobile\token-manager-app\bump-and-build-release.bat
Mobile\kilauea-alerts-android\bump-and-build-release.bat    (native Kotlin; no web/cap step)
```
Each one:
1. Bumps `versionCode` +1 and the last segment of `versionName` +1 in `Mobile/<app>/android/app/build.gradle`.
2. Writes the same `version` into `Mobile/<app>/package.json` and `Web/apps/<name>-web/package.json`.
3. Runs `pnpm install && pnpm run build` in `Web/apps/<name>-web/`.
4. Runs `pnpm install && pnpm exec cap sync android` in `Mobile/<app>/`.
5. Runs `gradlew.bat bundleRelease assembleRelease --no-daemon`.
6. Stages `RootRecord-<Product>-<version>.apk` + `.aab` under `Mobile/builds/<sub>/`.
7. Refuses to ship any file with `debug` in the name; tags `-unsigned` if signing is not wired.

### Build all Android apps in one shot
```
powershell -NoProfile -ExecutionPolicy Bypass -File Mobile\scripts\build-all-release-to-builds.ps1
```
Builds web → cap sync → Gradle release for token, account, business, weather, then the native Kilauea Alerts project. Does **not** bump versions.

## Release artifact staging

- Canonical staging root: `Mobile\builds\<token-manager|account-hub|business-manager|weather-manager|kilauea-alerts>\`
- Filename pattern: `RootRecord-<Product>-<version>.apk` / `.aab`
- Per-app `Mobile/<app>/release/` (Weather) is local-only and ignored by git.

## Source-of-truth notes

- **APIs**: prefer the per-product shards (`rootrecord-api-<product>`). `api.rootrecord.info` may still map to legacy `rootrecord-primary` during migration. New features go on the shards.
- **Web app shared frontend libraries**: each web app is a standalone pnpm project (its own `node_modules`, its own lockfile).
- **Shared Worker utilities** (e.g. `password-verify.ts`): live in `Web/cloudflare/shared/`. `cloudflare-update-workers.bat` runs `npm ci` there before deploying any Worker.

## Secrets / safety

Never commit:
- `credentials.env`, `.env*` (except `*.example`)
- Android keystores, `local.properties`, `keystore.properties`
- Firebase service account JSON (`*-adminsdk-*.json`, `google-services.json` is allowed but is gitignored in most subprojects)
- Stripe / API tokens of any kind

The repo-root `.gitignore` plus per-area `.gitignore` files handle this. Env edits should always be surgical (touch only the keys you mean to change).

## Marketing site checklist (`Web/main/`)

**For [rootrecord.info](https://rootrecord.info) (marketing Pages).** Expand in `Doc-Repo/` as needed.

### 1. Core structure and file organization
- [ ] Folder layout aligned with `Web/main/`
- [ ] Shared components (nav/footer) — reduce duplication
- [ ] Dead files: `.banner` CSS if unused, legacy files

### 2. HTML structure and semantics
- [ ] Consistent DOCTYPE/head, semantic tags
- [ ] Legal dates synced

### 3. CSS and styling
- [ ] Dark neon theme strong
- [ ] Add hero/image classes as needed

### 4. Functionality and features
- [ ] Dynamic nav, CTAs, account handoff

### 5. Performance and SEO
- [ ] OG tags, Schema.org, sitemap

### 6–10. Accessibility, graphics, perf
- [ ] Track detailed items in Doc-Repo when you start a pass

## Workflow rules (for assistant + dev)

- Only the human runs `commit-all.bat`, `cloudflare-update-*.bat`, and the `bump-and-build-release.bat` scripts.
- The assistant edits files and may suggest commit messages but does not push, deploy, or amend commits unless explicitly told to.
- Minimal diffs scoped to the area being worked on (`Mobile/`, `Web/`, `Doc-Repo/`, `solana-rootrecord-site/`). Avoid cross-area churn in a single commit.
- See `.cursor/rules/rootrecord-workspace.mdc` and `.cursor/rules/mobile-build-outputs.mdc` for the full set of conventions.
