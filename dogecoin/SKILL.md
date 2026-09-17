---
name: dogecoin
description: >-
  Dogecoin Core Qt on this OmniBook. Opens the datadir picker, then the
  node. Use when asked to turn Dogecoin on or pick the chain directory.
  Never sends, never dumps keys, never starts a miner.
---

# dogecoin

This folder **is** the node prefix. Do not dump RPC passwords or wallet keys. Do not invent balances.

Official Core **1.14.9** (`bin/dogecoin-qt`, `dogecoind`, `dogecoin-cli`). Qt `-choosedatadir` is how the operator picks the chain directory. That is usually on Archives (`/mnt/Archives/...`), not this NVMe. River car 12V (`ecoflow-river-car`) only while the disk is needed.

## Open (run this)

```bash
~/.ollama/skills/dogecoin/scripts/open-qt.sh
```

Forbidden: dumpprivkey, dumpwallet, send*, importprivkey. Mining is `xmrig`, not this desk.

Litecoin is `ltc-node`. Do not mix datadirs.
