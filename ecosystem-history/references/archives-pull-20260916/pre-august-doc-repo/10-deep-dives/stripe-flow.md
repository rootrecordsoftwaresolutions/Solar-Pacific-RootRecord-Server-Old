# Stripe checkout flow (simplified)

1. Authenticated user requests checkout session creation from API (`createStripeSubscriptionCheckout`).
2. Worker uses **`STRIPE_SECRET_KEY`** server-side—never ship it to clients.
3. User completes Stripe-hosted checkout.
4. Webhooks (if configured) or entitlement refresh paths update plan state—verify actual implementation in Worker billing modules.

## Teaching finance stakeholders

- **MRR** views live in Stripe dashboard.
- **Feature gates** should read **entitlement** endpoints, not Stripe directly from mobile apps.

## Related reading

- [../04-accounts/billing-and-licensing.md](../04-accounts/billing-and-licensing.md)
