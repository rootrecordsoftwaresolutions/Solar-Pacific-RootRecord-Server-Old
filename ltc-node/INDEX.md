# Desk — ltc-node

Litecoin node + unMineable pending + equal council vote (Carly, Bruce, Ava).
Datadir: `/mnt/Archives/Litecoin`. CLI often missing until the disk is up.
Snapshot: `scripts/pending_balance.py` → `state/pending_balance.json`.
Vote: `scripts/earnings_vote.py` → `state/earnings_vote.json`.
Send after a passed `ops`/`goals` motion: `ltc_status.py send`. Not user payouts.
