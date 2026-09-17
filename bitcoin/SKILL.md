---
name: bitcoin
description: >-
  Bitcoin Core Qt on this OmniBook. Opens the datadir picker, then the
  node. Use when asked to turn Bitcoin on or pick the chain directory.
  Never sends, never dumps keys, never starts a miner.
---

# bitcoin

This folder **is** the node prefix. Do not dump RPC passwords or wallet keys. Do not invent balances.

Official Core **31.1** (`bin/bitcoin-qt`, `bitcoind`, `bitcoin-cli`). Qt `-choosedatadir` is how the operator picks the chain directory. That is usually on Archives (`/mnt/Archives/...`), not this NVMe. River car 12V (`ecoflow-river-car`) only while the disk is needed.

## Open (run this)

```bash
~/.ollama/skills/bitcoin/scripts/open-qt.sh
```

Forbidden: dumpprivkey, dumpwallet, send*, importprivkey. Mining is `xmrig`, not this desk.

Dogecoin is `dogecoin`. Litecoin is `ltc-node`. Do not mix datadirs.
