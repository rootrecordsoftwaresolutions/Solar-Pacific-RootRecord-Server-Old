# 👀 eyes reaction — use often (2026-08-02)

Alex pointed at **👀** on Ava's messages: use it **also**, and **often**.

## Behavior
- Inbound seen/stored: **⏱️ + 👀**
- No-reply soft ack: ⏱️ + 👀 + heart
- Ava outbound Discord/Slack posts: **👀** on her own message
- Phase catch-up: fill missing 👀 even if ⏱️ already present

## Code
`src/ackReact.mjs` → `reactEyes` / `reactSeen` · wired in `avaPost.mjs`, `pipeline.mjs`, `phaseCatchup.mjs`
