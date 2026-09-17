# Privacy & security posture

This page frames **how to talk about** RootRecord security—**not** a legal privacy policy. Always link users to **`/privacy.html`** on rootrecord.info for binding text.

## Data minimization mindset

- Ask **why** each field is collected.
- Prefer **device-generated IDs** where dedupe needs them, not marketing fingerprints.

## Secrets hygiene (engineering)

Never commit:

- `credentials.env`, `.env*`, keystores, Firebase JSON, Stripe keys, wallet keys.

Env edits should be **surgical**—only the keys requested.

## Wallet & crypto

- **Token Manager** and **Solana Tools** emphasize **non-custodial** flows for user keys.
- **Custodial** or treasury flows exist for specific programs—those carry **different** risk disclosures.

## API abuse

Workers should rate-limit or gate privileged routes (`verifyWorkerOpsAdmin` patterns). Admin secrets belong in **Wrangler secrets**, not repos.

## Incident response (outline)

1. Rotate compromised secrets.
2. Invalidate sessions if JWT secret rotated.
3. Patch route + deploy Worker.
4. Communicate per legal/comms guidance.

## Related reading

- [../03-platform/data-and-storage.md](../03-platform/data-and-storage.md)
