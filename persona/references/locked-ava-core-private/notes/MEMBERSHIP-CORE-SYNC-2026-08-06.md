# Membership core sync — Root Record ↔ RootMC

**Live on Ava core** (`membershipSync.mjs` + cron `membership-core-sync` every 2m).

## Rules
- Join key: `discord_account_links.discord_user_id` (both D1s)
- Entitlement = union: life OR pro_unlocked OR active sub / redeemed / paid windows
- Grant-only sticky `MAX` on `pro_unlocked` + `life_member`
- Never copies Stripe customer/subscription fields
- Life on either side → life+pro on both

## First apply
Discord `1497037418979786823` (rootrecorddev):
- RR already life+pro
- RootMC granted life+pro on linked MC synthetic email

## Ops
- Log: `data/membership/core-sync.jsonl`
- Force: `node -e "import('./src/membershipSync.mjs').then(m=>m.syncMembershipCore({dryRun:false}))"` from core
- Dry: `AVA_MEMBERSHIP_SYNC_DRY=1` skips writes in cron
