---
name: holding
description: >-
  Static holding page for rootrecord.cloud downtime. Use when editing the
  “we’ll be back” HTML or the holding worker mirror.
---

# holding

This folder is the ops desk. Edit and publish from `site/` only — that is the
git root (`site/.git` → `Ava-Core-Dev/holding`). Do not use a second clone.

Vercel format: static `index.html` + `vercel.json` at `site/`. Root Directory `.`.
Live visitor worker still deploys from the `cloudflare-workers` skill
(`wrangler.rootrecord-cloud.toml`).

Topic index: `public-edge`.
