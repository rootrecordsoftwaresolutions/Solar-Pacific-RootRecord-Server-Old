# Soft praise replies (2026-08-02)

**Bug:** Compliments like "You are perfect Ava" fell through `softChatReply` → canned `mm?`. Alex: "work on this response."

**Fix (`classify.mjs`):**
- Praise / warmth → short warm soft replies (never `mm?`)
- Praise stays soft chat (≤120 chars, no `?`)
- "work on this response" / "fix this response" → `config_tune` (`fix_response`) — redo the reply, don't Root-Server dig

**Public redo:** Fern Forest TG — proper thank-you to Crazychickenlady12.
