# Phase 0 findings (2026-07-24)

| Probe | Result |
|-------|--------|
| `GET https://api.rootmc.info/health` | **429 Too Many Requests** |
| `GET https://api.rootmc.info/api/rootmc/server/config` | **429 Too Many Requests** |
| `GET https://rootmc.net/api/rootmc/server/config` | **200** but returned **homepage HTML** (Pages Function upstream failing / not JSON) |

**Verdict:** Blank site data matches Cloudflare Worker/API throttling (usage limits / rate limit), not empty D1 tables alone. Local-primary edge is the correct remediation path.
