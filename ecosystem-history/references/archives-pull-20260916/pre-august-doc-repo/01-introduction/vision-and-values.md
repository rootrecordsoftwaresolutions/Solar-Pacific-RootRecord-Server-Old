# Vision & design themes

This page is **interpretive**: it connects recurring themes across RootRecord’s apps and site copy. It is not an official mission statement unless leadership publishes one elsewhere.

## Grounded productivity

**Business Manager** frames itself around being a “grounding root” for business work—time, money, clients, inventory, scheduling. The visual language (teal, structured dashboards) reinforces *clarity* over novelty.

**Weather Manager** targets people who need **hazard awareness** without drowning in tabs—alerts, conditions, and environmental signals presented for quick decisions.

Together, these say: RootRecord’s productivity tools aim at **independent operators** and **serious hobbyists** who want capability without enterprise bloat.

## Optional cloud, explicit accounts

The marketing site highlights **local roots** and **optional cloud**—a promise that the stack should support users who want device-native workflows but still benefit from sign-in, sync where implemented, and billing when they choose paid plans.

Technically, that maps to:

- JWT/session-style auth against the primary API for mobile apps.
- Entitlement checks so paid features stay enforceable server-side where required.

## Distinct apps, one account idea

**Account Hub** exists so users do not have to remember which app owns “my subscription” or “my email.” It is intentionally **not** a duplicate of Weather or Business feature sets—it is the **spine** for account-level concerns.

## Solana: clarity and non-custodial posture

Where Solana appears (**Token Manager**, public **Solana Tools** site), the product PRDs stress:

- **Non-custodial** flows: do not collect seed phrases in-app.
- Use **standard wallet connections** (e.g. Phantom) and clear security copy.

Custodial or treasury-adjacent mechanics may exist server-side for specific features; treat those as **documented exceptions** with their own risk profile, not as the default user story.

## Design systems are allowed to differ per app

RootRecord intentionally uses **different palettes** per Android app so users can tell them apart in the multitasking view. That is a UX choice: *family of products*, not *one flat theme everywhere*.

## How to use this page

When writing user-facing copy or in-app help, ask:

1. Does this sound **grounded** and **honest**?
2. Does it avoid implying **guaranteed** cloud backup where none exists?
3. For money or crypto: is the **risk** visible?

If yes, you are aligned with the themes this doc draws from the codebase and PRDs.
