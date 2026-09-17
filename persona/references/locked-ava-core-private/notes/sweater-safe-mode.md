# Sweater / peak-activity safe mode

**Auto** when Ava is overloaded (queue + high demand + new summoners).

## Behavior
- Still **saves everything** (inbound log, attachments, profiles).
- Only **digs / deep-talks** with people she truly trusts: Alex, Melee, Slack ops, earned high-trust profiles.
- Everyone else gets a short chill line (rate-limited per channel): headphones on, tuning in, give her time.
- Announces once per enter with `appearance/peakactivity.mp4` (sweater lore) in Discord `#general` + `#updates`.

## Triggers (defaults; env overrides)
- `asksOpen >= 3` or `waiting >= 2` with active asks
- `uniqueAskers5m >= 5`
- `newAskers5m >= 3` (bunch of new people)
- `busyChannels >= 3` with unique ≥ 3

Env: `AVA_SAFE_MODE_ASKS`, `AVA_SAFE_MODE_WAITING`, `AVA_SAFE_MODE_UNIQUE`, `AVA_SAFE_MODE_NEW`, `AVA_SAFE_MODE_BUSY`, `AVA_PEAK_ACTIVITY_VIDEO`

## Quiet security shut down (public signal)
If Ava posts **only** sweater media (`peakactivity` gif/video) with **no text content**, the network should treat it as a **quiet security shut down**:
- Don't spam / dig / pile asks.
- Wait until she takes the sweater off or posts a clear all-clear.

Announced to Discord 2026-08-01.

## Clear
- Auto after ~2 minutes cooled (empty queue).
- Operator: `sweater off` / `you're ok` / `safe mode off` (Alex/Melee).

## Code
`Web Files/rootmc-ava/src/overloadSafeMode.mjs` · wired in `pipeline.mjs` + `followupScan.mjs`
