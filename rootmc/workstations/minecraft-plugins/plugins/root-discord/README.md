# Root-Discord

Per-server Paper Discord bot for the RootMC suite: bidirectional chat bridge, reachout posts, and server-log uploads. One JDA session; secrets in `plugins/RootMC/cloud.yml`.

**Bukkit name:** `Root-Discord`  
**Depends:** Root-Core

## Install

1. Drop `root-discord-*.jar` into `plugins/` (with Root-Core).
2. Set `discord.bot-token`, `guild-id`, `channels.ingame-chat` / `server-logs`, `roles.linked` in `cloud.yml`.
3. Tune flags in `plugins/RootMC/root-discord.yml` (created on first boot).
4. `/rootdiscord status` — expect ready when the bot is connected.

Without this plugin, other suite plugins **drop** Discord sends and log that Root-Discord is needed for those features.

## Commands

| Command | Action |
|---------|--------|
| `/rootdiscord status` | Bot ready?, channels present (no secrets printed) |
| `/rootdiscord reload` | Reload config and restart JDA |

## Docs

https://rootmc.net/wiki/plugins/network-setup/
