# radar-gif (device stub)

**AWS-only media path.** OmniBook does not pull or post radar GIFs.

| Piece | Where |
|-------|--------|
| NWS pull → `Current.gif` | AWS `rr-radar` / `bin/radar_poll.py` |
| Keyword → `sendDocument` + caption | AWS `rr-chat` / `bin/chat_poll.py` |
| AI chat | OmniBook council (no GIF attach) |

Caption (AWS):

> Here's the latest radar and weather information for you. Let me know if theres anything else I can relay.
