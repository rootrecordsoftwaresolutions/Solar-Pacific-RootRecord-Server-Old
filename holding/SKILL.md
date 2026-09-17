---
name: holding
description: >-
  Static holding page for rootrecord.cloud downtime. Use when editing the
  “we’ll be back” HTML or the holding worker mirror.
---

# holding

This folder is the ops desk. The page is `site/index.html` (git: `Ava-Core/sites/holding`).

Vercel format: static `index.html` + `vercel.json` at that root. Live visitor worker still deploys from the `cloudflare-workers` skill (`wrangler.rootrecord-cloud.toml`).

Topic index: `public-edge`.
