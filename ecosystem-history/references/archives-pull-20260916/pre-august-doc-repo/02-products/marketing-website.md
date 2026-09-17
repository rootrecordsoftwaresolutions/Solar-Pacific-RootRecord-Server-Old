# Marketing website (rootrecord.info)

The public site is a **static HTML + CSS** experience deployed with **Cloudflare Pages** from `Web/main/` in the web workspace. It is the **canonical marketing surface**: products, pricing, FAQ, contact, legal, and the account portal pages.

## Responsibilities

1. **Storytelling** — Explain Business Manager, Weather Manager, and ecosystem pieces with professional copy and strong visual design (2026 redesign: dark neon brand, Fraunces + Geist + JetBrains Mono).
2. **Account entry** — `account.html` + `account.js` talk to the configured license/API base (see `/api/site-config` function) for login, signup, and billing affordances.
3. **Compliance** — Host Terms and Privacy pages with stable URLs.

## Architecture constraints

Deploy must remain compatible with **Pages + functions**. Sensitive refactors to `account.js` selectors or site-config contracts can break production sign-in—treat those files as **integration surfaces**.

## Preview / local dev

Some repos include a **FastAPI stub** and static server for local previews; production is always **Cloudflare**.

## Related reading

- Domains: [../03-platform/domains-and-urls.md](../03-platform/domains-and-urls.md)
- Workers vs Pages: [../03-platform/cloudflare-workers.md](../03-platform/cloudflare-workers.md)
