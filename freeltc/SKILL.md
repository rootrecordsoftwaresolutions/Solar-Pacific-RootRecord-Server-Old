---
name: freeltc
description: >-
  FreeLTC public product (in development): freeltc.site, unMineable XMRig
  signup page, later a Solana token. Use when asked about FreeLTC. Not the
  Litecoin full node and not the operator xmrig desk.
---

# freeltc

**In development.** Do not treat this as live on the public internet
until a deploy is checked. Do not dump wallet keys or pool user strings.

This is a RootRecord product skill. It is not `ltc-node` (full node on
`/mnt/Archives/Litecoin`) and not the OmniBook operator miner (`xmrig`).

## What is on this desk

Pulled 2026-09-16 from `/mnt/Projects/FreeLTC`. Newer zip
(`freeltc.site (2).zip`, 13:05 HST) is what landed in `site/`:

| File | Role |
| --- | --- |
| `site/index.html` | Email gate, then mining how-to |
| `site/tutorial.html` | unMineable + XMRig walkthrough |
| `site/install-linux.sh` | One-shot Linux XMRig installer |
| `site/install-windows.ps1` | Windows installer |

Publish from `site/` only (`site/.git` → `Ava-Core-Dev/freeltc-site`). No second clone.

Hand notes on the drive: buy `freeltc.site`, point the unMineable referral at
the page, email signup to reveal the how-to, later a Solana token on that
domain. Domain price notes were on the scrap file; do not invent a live DNS
check.

Installer referral lives in those scripts. Do not paste it into visitor chat
unless you are editing the installer.

## Rules

1. Still building. No fake “site is up” unless curl says so.
2. Operator CPU mine on this laptop: `xmrig` skill. FreeLTC installers are for
   **visitors** on their own PCs.
3. Chain status / balance: `ltc-node` after sync. Never send, never dump keys.
4. Blueprint markdown that sat next to FreeLTC on the disk is **old OmniBook
   planning**, not this product. History copy:
   `ecosystem-history/references/projects-pull-20260916/blueprint/`.

Topic: `ltc-node` for the node. `xmrig` for the desk miner.
