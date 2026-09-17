# Cronologicals

When-folders from the old Ava-Core `operations/cronologicals/` map. Live jobs are skill `scripts/` plus origin scheduler, not `apps/core/crons/`.

| Folder | When |
| --- | --- |
| `always-on/` | Keeps running |
| `since-last-fire/` | Interval / hourly |
| `on-time/` | Clock (HST) |
| `in-order-on-boot/` | Once when origin starts |

Night sleep skips Ava scheduler jobs.
