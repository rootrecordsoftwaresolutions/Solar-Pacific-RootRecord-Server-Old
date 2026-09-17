# Account Hub

**Account Hub** is the **central mobile app** for RootRecord account concerns: identity, earn snapshot, subscription entry points, security settings, notification preferences, and **shortcuts into Weather and Business** via intents / URL schemes with Play Store fallbacks.

## Why it exists

Without a hub, users blame the wrong app when:

- password changes are confusing,
- subscription status is unclear,
- they cannot find “which app is logged in.”

Account Hub **does not** re-implement full Weather or Business feature sets—those remain first-class apps.

## Tabs (MVP mental model)

| Area | Purpose |
|------|---------|
| **Home** | Identity + earn summary + quick navigation |
| **Apps** | Connected RootRecord apps and deep links |
| **Security** | Password change, session hygiene, sign-out |
| **Account** | Profile shell, version, support links |
| **Subscription** | Plan display + billing portal handoff on rootrecord.info |
| **Notifications** | Push/email preferences via `/api/me/prefs` patterns |

Some endpoints may still be **roadmapped** in the app repo (`API-PROPOSALS` style docs)—check the latest source before promising features.

## Visual identity

Dark base with **amber** accents—deliberately distinct from Weather’s greens and Business’s teal so Android recents show **three different** apps at a glance.

## Related reading

- Authentication: [../04-accounts/authentication.md](../04-accounts/authentication.md)
- Billing: [../04-accounts/billing-and-licensing.md](../04-accounts/billing-and-licensing.md)
