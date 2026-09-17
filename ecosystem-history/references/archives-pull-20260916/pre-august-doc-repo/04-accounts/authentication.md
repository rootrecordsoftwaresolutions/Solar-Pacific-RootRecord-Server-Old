# Authentication model

RootRecord mobile apps use a **cloud-backed account** model: user signs up or logs in, receives a **bearer token** (JWT pattern), and sends it on subsequent API calls.

## Happy path (conceptual)

1. Client collects credentials (email/password or whatever the site/app implements).
2. Client calls auth endpoints on **`api.rootrecord.info`** (see Worker `primary-auth` modules).
3. Server validates password hash rules, issues token, persists session if applicable.
4. Client stores token locally (`localStorage` in web wrappers).
5. Authenticated routes resolve **user id** from `Authorization: Bearer …`.

## Sessions & devices

Advanced session listing / per-device revoke may be **roadmapped**—check Account Hub `API-PROPOSALS` before promising UI.

## Logout

Routes exist for logout semantics; “logout everywhere” may require explicit backend support—verify before UX claims.

## Security expectations

- Use **HTTPS only** in production.
- Rotate JWT secrets when personnel changes justify it.
- Never log bearer tokens in plain text.

## Related reading

- [billing-and-licensing.md](billing-and-licensing.md)
- [privacy-and-security.md](privacy-and-security.md)
