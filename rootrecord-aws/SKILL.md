---
name: rootrecord-aws
description: >-
  RootRecord AWS always-on collector (rr-aws / 3.16.29.76): weather, quakes,
  radar, hurricane, NOAA, Icecast radio, chronological drop-ins, Telegram
  datapacks. Local ingest/publish clock. SSH admin-only; EcoFlow stays local.
  AI reference: references/AI-SERVER.md. FileZilla: ~/Documents/rootrecord-aws-filezilla.env
---

# rootrecord-aws

This folder is the ops desk for the RootRecord AWS always-on collector.

**AIs:** read `references/AI-SERVER.md` before changing pollers, timers, radio, or deploy.
**Humans:** `references/OPERATOR.md`. FileZilla logins: `~/Documents/rootrecord-aws-filezilla.env`.

## Strict rules

- No local → AWS data copy. AWS starts empty.
- Zip → Telegram transfer channel → wipe AWS work tree. No AWS backup store.
- SSH is admin-only. Datapath is Telegram zips.
- Soft-park local overlapping pollers with `OFFLOADED`; never delete skill trees.
- EcoFlow stays on AVA-CORE (BLE).

## Layout

- `aws/` — code deployed to `/home/ubuntu/rootrecord/` on EC2 (`rr-aws`)
- `local/` — AVA-CORE ingest / prep / publish / trigger watch / catch-up
- `references/AI-SERVER.md` — machine-oriented server map for agents
- Host: `rr-aws` → `3.16.29.76` (Ubuntu, user `ubuntu`)

## Clock (HST)

- Ingest: `:10` `:25` `:40` `:55`
- Publish: `:00` `:15` `:30` `:45`

Topic index: `public-edge`.
