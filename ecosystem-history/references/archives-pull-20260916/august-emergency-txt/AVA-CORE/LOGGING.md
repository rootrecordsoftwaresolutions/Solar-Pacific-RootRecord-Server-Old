# Ava logging (hardened 2026-08-05)

## Canonical root
- `AVA_HANDOFF=/home/ava-core/ava`
- `Server Handoffs/Ava Ivy` → symlink to `/home/ava-core/ava`

## Streams
| File | Role |
|------|------|
| `data/logs/inbound.jsonl` | Watched ingest |
| `data/logs/outbound.jsonl` | Utterances |
| `data/logs/actions.jsonl` | Universal action bus (`level` field) |
| `data/logs/ops.jsonl` | Cron + local-api + platform ops |
| `data/logs/host-audit.jsonl` | 15m host flight |
| `data/flight/brain-events.jsonl` | Cross-brain + ops failures |
| `data/flight/patches.jsonl` | Self-restart / patch notes |
| `data/logs/archive/*.jsonl.gz` | Rotated chunks |
| `data/logs/index.sqlite` | Queryable index |

## Services
- `ava-ivy.service` — journal stdout/err
- `ava-local-api.service` — journal + ops.jsonl

## Daily
- `errorDigest.mjs` — TG skim of warn/error (ack-react noise filtered)
- rotate + index sync on flight interval + digest

## Query
```bash
cd /home/ava-core/ava/core && node scripts/log-query.mjs 24
```
