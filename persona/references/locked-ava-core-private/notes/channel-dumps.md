# Channel dumps (periodic)

Ava dumps Discord + Slack to text files and sends them to Telegram (`AVA_TELEGRAM_OPERATOR_IDS`).

## Full dump (manual)

Already done once under `reports/channel-dumps-2026-08-02/`.

## Incremental (automatic)

Wired into Ava poller:

- First run ~5 minutes after boot: **seed watermarks** at current tip (no re-send of history)
- Then every **2 hours**: dump **only new messages** → `reports/channel-dumps/incremental/<stamp>/`
- Telegram gets INDEX + MASTER + Discord/Slack bundles when `newCount > 0`
- Silent skip when nothing new

### Env

| Var | Default | Meaning |
|---|---|---|
| `AVA_CHANNEL_DUMP_MS` | `7200000` (2h) | Interval between incremental dumps |
| `AVA_CHANNEL_DUMP_BOOT_MS` | `300000` (5m) | Delay after boot before first seed/dump |
| `AVA_TELEGRAM_OPERATOR_IDS` | required | Who receives the files |

### Manual

```bat
cd "Web Files\rootmc-ava"
node scripts/dump-channels-incremental.mjs --seed
node scripts/dump-channels-incremental.mjs
```

Watermarks: `Server Handoffs/Ava Ivy/data/channel-dump-watermarks.json`
