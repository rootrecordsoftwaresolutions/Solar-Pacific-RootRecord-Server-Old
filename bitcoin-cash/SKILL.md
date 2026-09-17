---
name: bitcoin-cash
description: >-
  Bitcoin Cash Node Qt on this OmniBook. Opens the datadir picker, then
  the node. Use when asked to turn Bitcoin Cash on or pick the chain
  directory. Never sends, never dumps keys, never starts a miner.
---

# bitcoin-cash

This folder **is** the node prefix. Do not dump RPC passwords or wallet keys. Do not invent balances.

Official **Bitcoin Cash Node 29.1.0** (`bin/bitcoin-qt`, `bitcoind`, `bitcoin-cli`). Same binary names as Bitcoin Core — use this path, not `bitcoin`. Qt `-choosedatadir` picks the chain directory. Usually Archives (`/mnt/Archives/...`), not this NVMe. River car 12V (`ecoflow-river-car`) only while the disk is needed.

## Open (run this)

```bash
~/.ollama/skills/bitcoin-cash/scripts/open-qt.sh
```

Forbidden: dumpprivkey, dumpwallet, send*, importprivkey. Mining is `xmrig`, not this desk.

Bitcoin is `bitcoin`. Dogecoin is `dogecoin`. Litecoin is `ltc-node`. Do not mix datadirs.
