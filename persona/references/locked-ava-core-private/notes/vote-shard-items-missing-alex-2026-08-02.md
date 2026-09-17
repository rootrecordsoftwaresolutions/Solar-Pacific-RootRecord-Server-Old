# Vote Shard physical items missing (Alex) — 2026-08-02

## Ask
Ban boats PROP thread: Alex (`Alexrs94` / Discord `1497037418979786823` / UUID `3e660994-b16c-4714-bc15-9081aa928729`) said he’s missing **renamed amethyst custom items** for all votes (cloud showed ~248 / ~38.44%).

## Model
- Each verified listing vote → `grantVoteShard` (+1 weight) into double `/ec`.
- Forms: Vote Shard / Vote Shard Block / Vote Geode (PDC on amethyst materials).
- Auto-condenses to one **Vote Geode** certificate; Council power = weight in `/ec` only.
- Plugin: `root-appreciation` `VoteShardService` / `VoteShardItem`; mint trigger from `root-play` `VoteRewardService`.
- Cloud: `rootmc_ec_vote_shards.weight` ← plugin sync of EC item power.

## Live reconcile (Claims Shockbyte MySQL)
| Source | Alex |
|---|---|
| `root_rewards_votes` | **249** (last `2026-08-03`) |
| `root_vote_shard_issued` | **empty** |
| `root_vote_shard_pending` | **0** |
| `root_double_ender_chests` | **no row** |
| D1 `rootmc_listing_votes` | **197** (lagging) |
| D1 `rootmc_ec_vote_shards.weight` | **248** (ghost vs empty `/ec`) |

Hyperdrive `rootmc_claims` mirror had rewards votes (~211 earlier) but **not** shard/EC tables — always check Shockbyte host DB for mint ledgers.

## Root cause
Physical Vote Shards were never issued/delivered for Alex on Claims. Cloud EC weight was a stale sync, not items in the chest. Join backfill (`ensureExactOwed`) should mint when `votes > issued` (issued=0).

## Fix / staged
- Hardened `ensureExactOwed` to also recover wiped `/ec` when issued/votes already count weight (`recover-ec:` pending).
- Staged Claims handoff: `root-appreciation-1.8.1.jar` (remove `1.8.0`). Needs Alex FileZilla + restart — not force-restarted.
- Web `public/plugins/manifest.json` bumped to 1.8.1 (site jar deploy still human).
- Next step for Alex: join Claims → `/ec` + `/voteshard power` (~249). Then upload 1.8.1 when convenient.
- 2026-08-02 ~23:56Z: Alex confirmed `/ec` has bonds/diamonds/appreciation tokens only — no Vote Shards (matches dig). Offered FileZilla `1.8.1`+restart remint **or** guarded Claims RCON give; awaiting greenlight. No Shockbyte bounce forced.
