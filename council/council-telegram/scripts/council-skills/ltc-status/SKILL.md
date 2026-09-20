# ltc-status

Litecoin full node on the external disk, plus council earnings.

Pool pending and the main CLI wallet are **Carly, Bruce, and Ava** coins.
Equal vote (2 of 3) decides keep_mining / hold / ops / goals. Ava votes
costs and upgrades, not sharing with users. Visitor LTC later comes inbound
for council goals. A passed ops or goals motion is what lets the CLI send.

Wait until `blocks == headers` and IBD is false before a balance read.
Still forbidden: dumpprivkey, dumpwallet, importprivkey. No miner from here.
CLI is often missing until the Litecoin volume is on River car 12V.

```bash
python3 ~/.ollama/skills/ltc-node/scripts/pending_balance.py
python3 ~/.ollama/skills/ltc-node/scripts/earnings_vote.py
```
