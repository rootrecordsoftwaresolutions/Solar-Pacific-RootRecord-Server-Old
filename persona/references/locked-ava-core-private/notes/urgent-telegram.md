# Personal Telegram — master ops with Alex

Ava treats `AVA_TELEGRAM_OPERATOR_IDS` as **master comms** for you.

## Online notice

When Ava finishes boot (or auto-wakes from sleep), she sends a short **“Ava — online”** ping to your personal Telegram. Once per process — not on every heartbeat.

## Urgent alerts (periodic)

- Every **30 minutes** (env `AVA_URGENT_TELEGRAM_MS`) Ava rebuilds the urgent list.
- **Sends only if** the list is non-empty **and** changed since last send.
- **Silent if** nothing urgent, or the same items/status as last ping.
- Sources: runtime (power-off / not-live / sweater), open jobs (`waiting_restart`, `staged`, `blocked`, …), standing registry (`data/urgent-registry.json`).

### Manual

```bat
cd "Web Files\rootmc-ava"
node scripts/urgent-telegram.mjs --dry
node scripts/urgent-telegram.mjs --force
```

### Resolve a standing registry item

Edit `Server Handoffs/Ava Ivy/data/urgent-registry.json` → set `"status": "done"`, or call `resolveUrgentRegistryItem(id)` from a script. Next digest will treat the set as changed (or go quiet if empty).
