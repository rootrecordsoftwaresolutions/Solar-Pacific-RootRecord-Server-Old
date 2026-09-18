---
name: rootrecord-aws
description: >-
  RootRecord AWS collector: weather/quake/radar pollers, Telegram datapacks,
  local clock ingest/publish. SSH admin-only. EcoFlow stays local.
---

# rootrecord-aws

This folder is the ops desk for the RootRecord AWS always-on collector.

## Strict rules

- No local → AWS data copy. AWS starts empty.
- Zip → Telegram transfer channel → wipe AWS work tree. No AWS backup store.
- SSH is admin-only. Datapath is Telegram zips.
- Soft-park local overlapping pollers with `OFFLOADED`; never delete skill trees.
- EcoFlow stays on AVA-CORE (BLE).

## Layout

- `aws/` — code deployed to `/home/ubuntu/rootrecord/` on EC2 (`rr-aws`)
- `local/` — AVA-CORE ingest / prep / publish / trigger watch
- Host: `rr-aws` → `3.16.29.76` (Ubuntu, user `ubuntu`)

## Clock (HST)

- Ingest: `:10` `:25` `:40` `:55`
- Publish: `:00` `:15` `:30` `:45`

Topic index: `public-edge`.
