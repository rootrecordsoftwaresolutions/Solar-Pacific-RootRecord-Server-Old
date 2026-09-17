# Ava Ops

Private Android operations panel for Ava and RootRecord.

This app is intentionally separate from the public RootMC Android app. It will
become the mobile replacement for the Ava Electron control surface.

## Current Slice

- Bluetooth-first provider ordering with localhost and Cloudflare fallbacks
- RootRecord production and test server/service cards
- Start, stop, and restart controls using the active transport
- Allowlisted RCON command entry point
- Operations API provider status and active transport reporting
- Local audit activity feed
- Live `/api/ops/mobile-dashboard` board (Bluetooth first, then LAN, then Cloudflare)
- Screens render only snapshot fields — empty/waiting when a feed is missing
- HTTP operations API client for laptop and future VPS endpoints
- Live Settings: feature toggles + Idle desk (`/api/ops/features`, `/api/ops/idle-stop`)

## BuildConfig bases

Debug builds use `http://10.0.2.2:8787` (emulator → host origin on `:8787`).

Release builds use LAN fallback `http://192.168.1.66:8787` (typical OmniBook desk IP)
**after Bluetooth**. Override per phone in Connection Settings.

Compile-time Cloudflare (`OPS_CLOUDFLARE_API_BASE_URL`) is empty until a real
remote host exists under existing brands (`rootrecord.info`, `rootmc.net` /
`play.rootmc.net` / `api.rootmc.net`, `ava.rootmc.net`). Do not invent DNS.
`ops.rootrecord.online` is not a RootRecord surface; the app rejects any base
URL containing that hostname.

## Build

The project requires Android SDK Platform 35 and Build Tools 35, plus JDK 17.
`ANDROID_HOME` / `sdk.dir` is `/home/rootrecord/.local/opt/android-sdk`.

```bash
. ~/.local/opt/rootrecord/android-build/android-env.sh
~/.local/opt/rootrecord/android-build/assemble.sh ava-ops
```

Version is `0.2.0` (`versionCode` 2) after the live Settings / Idle desk slice.

The generated APK is written to `app/build/outputs/apk/debug/`.

## In-App Dynamic Configuration

The app includes an in-app **Connection Settings** dialog accessible via the top-bar gear icon:
- **Paired Bluetooth Devices**: Displays paired workstation devices on the phone for 1-tap selection, plus manual MAC address / name entry.
- **Localhost / LAN API Base URL**: Configurable per device (e.g. `http://192.168.1.66:8787` on physical phones, `http://10.0.2.2:8787` on emulators).
- **Cloudflare API Base URL**: Leave blank until a public ops host exists.
- **Auth Token**: Optional shared token for bridge/API authentication.

Settings persist locally in private Android `SharedPreferences` on each phone, allowing seamless deployment to multiple personal devices without needing custom compile-time configs.

## Transport Priority

1. Bluetooth bridge (preferred at the desk)
2. LAN / localhost HTTP
3. Cloudflare only when a real base URL is configured

The host-side Bluetooth bridge accepts JSON-over-RFCOMM requests, validates a
shared token when configured, and forwards only approved ops to the local Ava
API: servers, perform, rcon, mobile-dashboard, features GET/POST, idle-stop.
Blog, AdSense, goal-drafts, and similar `/api/ops/*` routes stay desk/web only.

## Settings toggles

Phone Settings rows map to `GET`/`POST /api/ops/features`:

- `obs`, `radio_on_air`, `music_bed`, `morning_report`, `midday_report`,
  `startup_voice`, `cloud_report_spend`
- `companions_poller` — Discord/Slack poller; off at boot until flipped
- `local_edge` — local-edge gateway on this desk; off at boot until flipped

Idle desk calls `POST /api/ops/idle-stop`.

## Power

The EcoFlow Power screen is **snapshot-only** from the board. NightOps / BLE AC
control is a separate path — not in this app.
