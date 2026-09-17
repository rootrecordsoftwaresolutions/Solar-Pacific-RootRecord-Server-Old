# Telegram reactions as good replies

**Symptom:** Thumbs-up / heart on an Ava Telegram line did nothing for learning.

**Cause:** `getUpdates` only asked for `message`. Outbound sends were not keyed by `message_id`.

**Fix (2026-09-05):** send_message registers outbound; inbox accepts `message_reaction`; positive emoji → training gold.

**Needs:** Origin recycle so inbox picks up `allowed_updates`.

**Check:** React 👍 on a recent Ava bot message; look for `reaction_gold` in group inbound log.
