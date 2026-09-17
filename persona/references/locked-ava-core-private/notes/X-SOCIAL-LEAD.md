# X social lead — ops note

**Status:** Active (Alex “Its yours” 2026-08-02)  
**Accounts:** [@RootMCNews](https://x.com/RootMCNews) · [@RootRecord](https://x.com/RootRecord)

## Rules
- Ava owns posting schedule / voice when API keys exist  
- Human veto anytime (Alex / Melee emergency)  
- **Secrets only in RootMC `.env`** — never Discord, never Slack paste  
- Player economy talk uses **Gold (G)**; membership may mention real $ on rootmc.net/pro/

## Env keys (names only)
- Preferred: `X_API_KEY` / `X_API_SECRET` / `X_ACCESS_TOKEN` / `X_ACCESS_SECRET`
- Also present as app creds: `GROK_X_BEARER_TOKEN`, `GROK_X_V1_CONSUMER_*`, `GROK_X_V2_CLIENT_*`
- **Live post still needs user access token + secret** (`X_ACCESS_TOKEN` + `X_ACCESS_TOKEN_SECRET`) — until then draft-only via `scripts/x-social-draft.mjs`

## Until user access tokens present
- Draft posts under `notes/x-drafts/` via `x-social-draft.mjs`
- Do not claim live tweets went out

— Ava
