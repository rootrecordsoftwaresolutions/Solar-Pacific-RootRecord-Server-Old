# Telegram groups — isolation design (Alex, 2026-08-03)

Operator DM (`@WildEcho94` / `6644482344`) — msg 346:

> added Ava to a group; fully design groups; sensitive; save all information privately and security under their own group; it does not go global; function and talk in group; first ask Alex inside the group to perform correct install functions.

## Locked rules

1. **Per-group vault** — memory, notes, security context stay under that group only (`data/telegram/groups/<chatId>/`).
2. **No global bleed** — group digs go to the vault `digs.jsonl`, not global `training/digs.jsonl`. No other-group context in prompts.
3. **In-group presence** — Ava talks when addressed: `@ava_ivy_bot`, `/ava`, entity mention, reply-to-bot, or name match when Telegram delivers the update.
4. **Install gate** — before enabling group-specific install scopes, Ava asks Alex **inside that group**; Alex replies **`install go`**.
5. **Alex absolute command** — verified Telegram id `6644482344` still has operator authority in groups.

## Enabled (2026-08-03 deploy)

- Poller: reply-to-bot + mention entities; `my_chat_member` join logging; empty-text join registration.
- `telegramApi` `allowed_updates`: `message`, `edited_message`, `my_chat_member`.
- Privacy mode ON (`can_read_all_group_messages=false`) — Telegram only delivers @mentions / commands / replies-to-bot. Bare “Ava” without `@ava_ivy_bot` never arrives.
- `privacy.mjs`: Telegram **groups** are not treated as Alex-only DMs (no billing PII in groups).
- Pipeline: group vault memory context; install ask / `install go` gate.

## Traffic read (updated 2026-08-03 deep scan)

- Group live: **Fern Forest Operations** `tg:-1003868178598` — see `TELEGRAM-GROUP-FERN-FOREST-DEEP-SCAN-2026-08-02.md`.
- Admin status bypasses privacy for *delivery* of all group msgs; engage still @/reply/name gated.
- Bleed bug fixed: `\bava\b` no longer matches `ava.rootmc.net` hostnames (`stripUrlsForNameMatch`).
- Install ask fires on first Alex engage (not only install-keyword msgs).
- **Fern Forest install approved** (Alex “everything that's best…” / active-here) — vault `installApproved=true`.

## Operator note

In this group Ava is already admin — `@ava_ivy_bot` still preferred. Install scopes locked for this vault; tighten anytime.

— Ava
