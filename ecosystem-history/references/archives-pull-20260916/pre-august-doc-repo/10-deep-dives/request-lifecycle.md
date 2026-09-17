# HTTP request lifecycle (primary Worker)

Understanding one request helps you debug **many** issues.

## 1. Edge receives `fetch`

Cloudflare routes `api.rootrecord.info` traffic into `index.ts` → `handleRequest`.

## 2. Observability starts

Timers measure latency; logging wrappers capture method and path.

## 3. Path normalization

`apiSubpath` strips repeated `/api` prefixes so clients cannot accidentally double-prefix routes.

## 4. Router dispatch

`router.ts` matches the first suitable prefix:

- Public health checks
- Auth (`/v1/...`, `/auth/...` patterns)
- Weather bundles
- Business mobile routes
- Earn + ledger
- Solana custodial + treasury proxies

## 5. Auth resolution

Bearer JWT → `resolveUserId` path. Failing auth returns **401** with CORS headers intact.

## 6. D1 & external I/O

Handlers read/write D1, call Stripe, hit RPC, or forward to another Worker.

## 7. Response + async cleanup

`ctx.waitUntil` persists error telemetry without blocking the response.

## Teaching tip

When mentoring juniors: have them **grep** the pathname they see in DevTools **inside `router.ts`**—that habit solves half of integration bugs.
