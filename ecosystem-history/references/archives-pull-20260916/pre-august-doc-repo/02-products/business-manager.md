# Business Manager

**Business Manager** is RootRecord’s **Android-first** operations app for people who run a small business or serious side practice. It covers time tracking, money, clients, inventory, scheduling, work logs, and reporting.

## What problem it solves

Small operators often juggle **spreadsheets, notes apps, and ad-hoc tools**. Business Manager aims to put **time, money, and operational detail** in one mobile-first workflow with a coherent UI (teal palette, “grounding root” positioning).

## Architecture (conceptual)

- **Frontend:** React + Tailwind, Capacitor for Android. Hash routing is used where `file://` and Capacitor require it.
- **Backend in production:** The **primary Worker** at `api.rootrecord.info` — not a long-lived Node server on a VPS.
- **Auth:** RootRecord account + entitlement checks aligned with other apps.

Data for this app’s cloud features is stored against primary API tables such as **business-owned rows** (`bm_owned_row`–style naming in D1). Exact schemas belong in the Worker migrations—this doc stays conceptual.

## What you should teach new users

1. **Sign in** establishes identity and plan state.
2. **Dashboard** gives a snapshot (hours, income, expenses, net—depending on implementation version).
3. **Tabs** map to operational areas: tracking, money, schedule, more.

## Non-goals (typical)

- Replace enterprise ERP for multinational inventory.
- Be identical to legacy Windows Business Manager installers where those still exist—mobile scope can differ.

## Related reading

- Platform API: [../03-platform/api-overview.md](../03-platform/api-overview.md)
- Accounts: [../04-accounts/authentication.md](../04-accounts/authentication.md)
- Mobile releases: [../06-development/build-and-release-mobile.md](../06-development/build-and-release-mobile.md)
