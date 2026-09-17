# Billing & licensing

RootRecord monetizes through **subscriptions** and related Stripe flows. The **exact SKUs and prices** live in Stripe and marketing pages—this document explains the **mechanics**.

## Stripe inside the primary Worker

The Worker imports Stripe helpers (`billing-stripe`) to create **checkout sessions** for recurring prices. Configuration uses:

- `STRIPE_SECRET_KEY` (secret)
- `STRIPE_PRICE_ID` (price reference)

## Entitlements

Mobile apps call **`/api/auth/entitlement`** (or equivalent) to refresh what the user may access. **Never** trust client-side flags alone for paid gating—always reconcile server-side.

## Marketing site portal

`account.html` surfaces billing actions (portal buttons, status text). Keep DOM ids stable when redesigning CSS.

## Licence Worker

Some paths still route through **`rootrecord-license`** semantics during migration—treat licence vs primary as **integration detail**, not something end users need to name.

## Operational caution

Refunding, chargebacks, and proration are **Stripe dashboard** concerns with legal/accounting implications—document runbooks outside this repo if needed.

## Related reading

- [authentication.md](authentication.md)
