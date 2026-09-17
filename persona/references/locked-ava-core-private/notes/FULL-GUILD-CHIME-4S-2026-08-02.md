# Full-guild 4s scan + chime-in (2026-08-02)

**Order:** Scan everything every ~4s; respond when opportunity; chime in when useful.

## Behavior
- Hot poll / gateway: **~4s**
- Watch expands to **all guild text/announcement/forum/media** + active threads (`guildChannelWatch.mjs`)
- Engage if @Ava **or** `shouldAvaChimeIn` (help/questions/RootMC topics/keep-alive)
- Cooldowns per channel so she doesn't yap every line
- Still silent-unsolicited in `#admins`; `#random-facts` address-only
- `AVA_CHIME_IN=0` disables chiming (scan still full)

## Code
`src/chimeIn.mjs` · `src/guildChannelWatch.mjs` · poller `channelTargets` · pipeline engage gate
