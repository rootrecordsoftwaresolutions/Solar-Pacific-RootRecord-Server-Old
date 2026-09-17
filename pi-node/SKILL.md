---
name: pi-node
description: >-
  Official Pi Network Linux node CLI. Opens a directory picker, then
  initialize in a terminal. Use when asked to turn Pi Node on or pick
  the data directory. Never dumps NODE_SEED, never prints postgres
  passwords, never starts a miner.
---

# pi-node

This folder **is** the CLI prefix. Do not dump `NODE_SEED` or postgres passwords. Do not invent balances.

Official page: [minepi.com/pi-node](https://minepi.com/pi-node/) — **Pi Desktop 0.6.3**. WINDOWS/MAC download the Electron app. LINUX on that page is **not** 0.6.3; it is the apt CLI (`https://minepi.com/pi-blockchain/pi-node/linux/`). GitHub `pi-node/pi-node` 0.6.3 ships `.exe` and `.dmg` only.

Follow [Linux install](https://minepi.com/pi-blockchain/pi-node/linux/): apt.minepi.com, `sudo apt-get install pi-node`, then `pi-node initialize`. Docker Engine + Compose, not Podman. Official floor 150 GB — Archives, not this NVMe. River car 12V (`ecoflow-river-car`) only while the disk is needed.

This desk does **not** pass `--node-private-key` or `--postgres-password`.

## Open (run this)

Official apt + initialize (sudo password in the terminal):

```bash
~/.ollama/skills/pi-node/scripts/install-linux.sh
```

Picker-only if `pi-node` is already on PATH:

```bash
~/.ollama/skills/pi-node/scripts/open-qt.sh
```

Forbidden: print NODE_SEED, mainnet.env, stellar-core.cfg keys. Mining is `xmrig`, not this desk.

Bitcoin Cash is `bitcoin-cash`. Bitcoin is `bitcoin`.
