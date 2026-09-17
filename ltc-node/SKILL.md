---
name: ltc-node
description: >-
  Litecoin node, unMineable pending withdraw, and equal council vote on
  those earnings (Carly, Bruce, Ava). Use before any LTC balance or send.
  Never dump keys. Never start a miner from this desk.
---

# ltc-node

Do not invent balances. Pool pending + CLI main wallet are council
earnings. Carly, Bruce, and Ava each have one equal vote on what to do with
them. Dumpprivkey / dumpwallet / importprivkey stay forbidden.

Configured datadir is `/mnt/Archives/Litecoin`. CLI is often missing until
River car 12V brings the disk up. Media merge does not touch that datadir.

Main wallet (CLI): `LTC_MAIN_ADDRESS` in Ava-Core `.env`.

## Voices on this coin

- **Carly** — never thinks it is enough. Default `keep_mining`.
- **Bruce** — optimistic about the node/pool/CLI. Default `hold` or `ops`.
- **Ava** — always super excited. Default `ops` (our costs and upgrades).
  Not a user giveaway. Later visitor LTC inbound funds **their** goals.

Majority is **2 of 3**. Split = no motion. A passed `ops` or `goals` is what
allows `sendtoaddress` through this desk. Never pay users from this wallet.

```bash
python3 ~/.ollama/skills/ltc-node/scripts/ltc_status.py
python3 ~/.ollama/skills/ltc-node/scripts/ltc_status.py address
python3 ~/.ollama/skills/ltc-node/scripts/pending_balance.py
python3 ~/.ollama/skills/ltc-node/scripts/earnings_vote.py
python3 ~/.ollama/skills/ltc-node/scripts/ltc_status.py send <dest> <amount>
```

LLM file: `~/.ollama/skills/ltc-node/state/pending_balance.json`
Latest vote: `~/.ollama/skills/ltc-node/state/earnings_vote.json`

Cron `ltc-pending` (30m) refreshes pending, CLI address balance when the node
is up, and the equal vote. Auto-withdraw floor **0.00075 LTC**; percent may
exceed 100. Pool keys stay in `.env`. Never dump them.

CPU mining is `xmrig`. Public product draft is `freeltc`.
