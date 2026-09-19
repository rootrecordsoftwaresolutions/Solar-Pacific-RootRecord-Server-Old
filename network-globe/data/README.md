# Runtime data

This folder is populated automatically by `server.js` while the collector is running.

- `state.json` — latest live state.
- `history.json` — rolling recent snapshots.
- `geo-cache.json` — cached remote IP geolocation.

These files are intentionally not required for startup; the server creates/updates them automatically.
