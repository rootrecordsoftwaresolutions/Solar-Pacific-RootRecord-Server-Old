# Internal vs external Solana keys

## External (user wallet)

- User connects Phantom or similar.
- User signs transactions explicitly.
- RootRecord **never** receives the seed phrase in normal Token Manager flows.

## Internal (platform / treasury / custodial)

- Worker holds encrypted key material (`INTERNAL_WALLET_ENC_KEY_B64`, treasury secrets).
- Crons may move funds according to **program rules**.
- Mistakes here are **irreversible on-chain**—ops reviews required.

## Teaching script

> “If you clicked ‘Connect wallet,’ **you** sign. If the app says ‘we handle this payout internally,’ **the platform** signs—read the fine print.”

## Related reading

- [discord-roots-onboarding.md](discord-roots-onboarding.md) — custodial deposit wallet + `/swap` flow in Discord
- [../05-solana/solana-ecosystem.md](../05-solana/solana-ecosystem.md)
