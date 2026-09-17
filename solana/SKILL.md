---
name: solana
description: >-
  Agave Solana CLI on this OmniBook. Opens a directory picker for the
  ledger path. Use when asked to turn Solana on or pick the chain directory.
  Never sends, never dumps keys, never starts a miner or mainnet validator.
---

# solana

This folder **is** the CLI prefix. Do not dump keypairs or RPC secrets. Do not invent balances.

Official Agave **4.2.2** (`bin/solana`, `solana-keygen`, `solana-test-validator`, `spl-token`). There is no Qt. `scripts/open-qt.sh` uses zenity so you can pick the ledger directory the same way as Bitcoin/Dogecoin. That is usually on Archives (`/mnt/Archives/...`), not this NVMe. River car 12V (`ecoflow-river-car`) only while the disk is needed.

This desk does **not** start `agave-validator` or a mainnet catch-up. That would fill the laptop. Local test cluster is `solana-test-validator --ledger <dir>` only if you ask.

## Open (run this)

```bash
~/.ollama/skills/solana/scripts/open-qt.sh
```

Forbidden: print keypair JSON, dump seeds, send, airdrop from operator funds. Mining is `xmrig`, not this desk.

Bitcoin is `bitcoin`. Dogecoin is `dogecoin`. Litecoin is `ltc-node`. Do not mix datadirs.
