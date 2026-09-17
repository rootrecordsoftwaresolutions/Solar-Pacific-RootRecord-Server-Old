# In-game join welcome (template, no AI)

Alex 2026-08-02: lookout for joins via RCON; AI not needed for every task; improve new-player metrics; cute Ava hello.

Runtime: `Web Files/rootmc-ava/src/ingameJoinWelcome.mjs`
- Bridge `#ingame-chat` kind=join + RCON `list` delta
- First-time only → private `tell` welcome templates
- Metrics: `data/ingame-join-metrics.json`
- Off: `AVA_INGAME_JOIN_WELCOME=0`
