# Root Goals

**Root Goals** helps people define outcomes, break them into trackable steps, and get an AI-generated plan from their own inputs. **Root Goals 1.0.0 · Release 0** is the first versioned foundation.

## What problem it solves

- **Clarity** — Turn vague intentions into a named goal with purpose, steps, and timeline.
- **Persistence before signup** — Guest-first onboarding saves drafts via `X-Guest-Id` / `device_id` until the user creates an account.
- **AI plan** — Grok generates a summary, plan steps, **Actions**, and **Suggestions** from full goal context (multi-pass when validation fails).
- **Progress tracking** — Achievements, notes, costs, and income entries while working the goal.
- **Public sharing** — Optional public goals at custodial Solana address URLs on marketing site and `goals.rootrecord.info`.

## How it talks to the cloud

Clients call **`rootrecord-api-goals`** (`https://api-goals.rootrecord.info/api` in production; Workers dev URL for Pages until custom domain is attached). Authenticated routes use Bearer token or SSO cookie; drafts and pre-auth flows use **`X-Guest-Id`**.

Public read-only routes: `GET /public/:address/goals` and `GET /public/:address/goals/:slug`.

## AI disclaimer

Root Record does **not** provide financial support or advice. **Actions** and **Suggestions** are AI-generated and may not reflect Root Record's core beliefs. Use your own judgment.

## Tier limits (Release 0)

| | Free | Member / Lifetime |
|---|---|---|
| Active goals | 3 | 42 |
| AI refresh per goal | 1 / 3 days | 3 / day |

## Code locations

| Layer | Path |
|---|---|
| Web SPA | `Web/apps/root-goals-web/` → Pages `rootrecord-goals-web` |
| Android | `Mobile/root-goals-mobile/` (`com.rootrecord.rootgoals`) |
| API Worker | `Web/cloudflare/rootrecord-api-goals/` |
| Marketing public HTML | `Web/main/root-goals/` + Pages Functions |

## Related reading

- Product contract: [Web/main/root-goals/README.md](../../../Web/main/root-goals/README.md)
- Release 0 Discord copy: [Web/main/root-goals/RELEASE-0-DISCORD.md](../../../Web/main/root-goals/RELEASE-0-DISCORD.md)
- API shards overview: [../03-platform/api-overview.md](../03-platform/api-overview.md)
