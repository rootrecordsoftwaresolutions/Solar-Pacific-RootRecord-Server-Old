# Discord ??? encoding fix (2026-08-02)

## Cause
Agent posts via PowerShell `Get-Content | node` mangled UTF-8 (emdash, middot, bullets, emoji) into `???` before Node/Discord saw them. Scrub ran too late on already-corrupted text.

## Fix
- `scrub.mjs` `discordSafeText` - flatten fancy punctuation to ascii
- Discord REST `Content-Type: application/json; charset=utf-8`
- `post-as-ava.mjs` / `edit-as-ava.mjs` prefer `--file` (Node reads UTF-8) over pipes

## Agent rule
Always: `node scripts/post-as-ava.mjs discord <channel> [ref] --file path.txt`

— Ava
