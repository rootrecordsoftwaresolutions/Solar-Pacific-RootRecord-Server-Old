# MOTD & in-game copy (templates)

Minecraft color codes: `§` — test on your client version.

---

## MOTD line 1 (examples)

```
§a§l{{SERVER_NAME}}§r §7— §f{{PRIMARY_MODE}}
```

```
§6§l{{SERVER_NAME}}§r §7· §a{{SERVER_IP}}
```

---

## MOTD line 2 (examples)

```
§7Vote §f{{VOTE_COMMAND}} §7· Map §f{{MAP_HOST}}
```

```
§7Discord linked · §b{{DISCORD_SHORT}}
```

---

## Recommended pair

**Line 1:** `§a§l{{SERVER_NAME}}§r §7— §f{{SUBTITLE_SHORT}}`

**Line 2:** `§7Vote §f{{VOTE_COMMAND}} §7· §f{{WEBSITE_HOST}} §7· §9Discord`

---

## server.properties MOTD field

```
motd=§a§l{{SERVER_NAME}}§r\n§7{{PRIMARY_MODE}} · §f{{SERVER_IP}}
```

---

## Join message (long)

```
Welcome to {{SERVER_NAME}}!
{{PRIMARY_MODE}} · Vote: {{VOTE_COMMAND}} · Rules: {{WIKI_OR_RULES_URL}}
Discord: {{DISCORD_INVITE}} · Map: {{MAP_URL}}
```

---

## Join message (short)

```
Welcome! {{VOTE_COMMAND}} · /help · {{WEBSITE}}
```

---

## Tab list

**Header:** `§a§l{{SERVER_NAME}}§r §8| §7{{TAGLINE_SHORT}}`

**Footer:** `§7{{WEBSITE_HOST}} §8· §9Discord §8· §a{{VOTE_COMMAND}}`

---

## Announcer lines (rotate)

```
§7Vote for rewards: §b{{VOTE_COMMAND}}
```

```
§7Map: §b{{MAP_URL}}
```

```
§7Rules: §b{{WIKI_OR_RULES_URL}}
```

```
§7Store: §b{{STORE_URL}}
```
*(Only if store exists — disclose pay-to-win honestly.)*

---

## Checklist

- [ ] MOTD fits client width
- [ ] Join message mentions vote + rules
- [ ] Match your essentials / announcer plugin config
