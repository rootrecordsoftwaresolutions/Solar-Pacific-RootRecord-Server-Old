# TG + Discord fixes — 2026-08-03 night

## Shipped
1. **Cloud-dark no longer mutes Telegram/Slack** — only Discord dream silence.
2. **Channel dumps** stay on E; TG file push off by default.
3. **Llama debug side-channel** default **off** (`AVA_LLAMA_TELEGRAM=0`).
4. **Brain tags** `[llama]`/`[cursor]` only when pinned (or `AVA_BRAIN_TAGS=1`).
5. **Scrub** protects brain tags (no `[cursor]` → `[Root Server]`).
6. **Soft chat** includes presence/affection/feelings → no dig instant-open spam.
7. **Dark-stall cooldown** (~15m/channel) — stops catchup flood of "both dark".
8. **Discord DMs** surface as `discord-dm` into recommend.
9. **TG offline copy** no longer uses Discord dream-dark blurb.
10. **ackReact** no-ops on Telegram channel ids (was calling Discord API).
11. **Work-ask** includes control panel / `.exe` / `:8787`.
12. **Brain swap** still live: `talk to llama` · `talk to cursor` · `brain auto`.

## Still open (ops, not code)
- `ava.rootmc.net` Linux cloudflared creds
- Dream/Grok credits (403 when unpaid)
- Root-Ava-Core plugin greenlight
