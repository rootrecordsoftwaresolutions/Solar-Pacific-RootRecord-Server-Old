# Ava Ivy — Durable IDs (no secrets)

## Bot / app / people

| Who | System | ID |
|-----|--------|-----|
| Ava Discord application / bot | Discord | `1532751879875072070` |
| Ava Slack bot user | Slack | `U0BMBNYPYA2` |
| Ava Slack app | Slack | `A0BMAC7NZD3` |
| Legacy RootMC Discord bot | Discord | `1511794429986345020` |
| Alex Discord | Discord | `1497037418979786823` (`rootrecorddev` / Alexrs94) |
| Alex Slack | Slack | `U0BLWBTGYTU` |
| Alex Telegram | Telegram | `6644482344` (`@WildEcho94`) |
| ZuppaFredda Discord | Discord | `788153722198294618` (never @ping) |
| RootMC guild | Discord | `1516108585740800042` |

## Discord channels

| Name | ID |
|------|-----|
| #general | `1516108586307158088` |
| #admins | `1516121832493678612` |
| #proposals | `1526664180491358419` |
| #governance | `1522406451413385317` |
| #voting | `1522413185364398090` |
| #constitution | `1522406019152478210` |
| #development | `1532929974154166522` |
| #memes-and-media | `1516389376198840421` |
| Ava media vault | `1533268458668687392` |
| #random-facts | `1531432703675596942` |
| #updates | `1520665313631408251` |
| #solar-server | `1533915343766949949` |
| Hourly snapshots | `1528956490831102093` |
| In-game chat bridge | `1516706598519832677` |

## Slack channels

| Name | ID |
|------|-----|
| #development-feed | `C0BMCPMDDQR` |
| #new-plugin-development-plans | `C0BM4P3GVDX` |

## Defaults (non-secret)

| Setting | Default |
|---------|---------|
| Brain port | `8787` |
| `AVA_MODEL` | `composer-2.5` |
| Local organizer URL | `http://127.0.0.1:11434` |

## Posting surfaces

- Discord → `AVA_DISCORD_BOT_TOKEN` via rootmc-ava post helpers
- Slack → `AVA_SLACK_BOT_TOKEN` + Socket Mode `AVA_SLACK_APP_TOKEN`
- Telegram → `AVA_TELEGRAM_BOT_TOKEN`
- **Never** Cursor Slack MCP for Ava voice (posts as human/Alex)
