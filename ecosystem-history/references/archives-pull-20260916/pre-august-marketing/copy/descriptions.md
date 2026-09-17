# Descriptions (templates)

Replace all `{{...}}` placeholders. Plain text unless the form accepts Markdown/HTML.

---

## Tagline (one line)

```
{{SERVER_NAME}} — {{SUBTITLE}} · {{SERVER_IP}}
```

**Example:**
```
AuroraSMP — Towny survival with economy & events · play.aurorasmp.example
```

---

## Ultra-short (~150 characters)

```
{{SERVER_NAME}} | {{PRIMARY_MODE}} | {{HOOK}} | Vote for rewards | {{SERVER_IP}} | {{DISCORD_INVITE}}
```

---

## Short (~350 characters)

```
{{SERVER_NAME}} is a {{MINECRAFT_VERSION}} {{PRIMARY_MODE}} server. {{HOOK_SENTENCE}} Features: {{FEATURE_LIST_SHORT}}. Vote daily for {{VOTE_REWARD_DESC}} — use {{VOTE_COMMAND}} in-game. Join {{SERVER_IP}} · {{WEBSITE}} · {{DISCORD_INVITE}}
```

---

## Medium (~750 characters)

```
Welcome to {{SERVER_NAME}}!

{{SERVER_NAME}} is a {{PRIMARY_MODE}} Minecraft server for {{TARGET_AUDIENCE}}. {{PARAGRAPH_ABOUT_SERVER}}

Features:
• {{FEATURE_1}}
• {{FEATURE_2}}
• {{FEATURE_3}}
• {{FEATURE_4}}
• {{FEATURE_5}}

Vote on server lists once per day for {{VOTE_REWARD_DESC}} — {{VOTE_COMMAND}} in-game or links on {{WEBSITE}}.

Rules: {{WIKI_OR_RULES_URL}} · Fair play, no cheating.

Join: {{SERVER_IP}}
Website: {{WEBSITE}}
Discord: {{DISCORD_INVITE}}
Map: {{MAP_URL}}
```

---

## Long (~1500 characters)

```
{{SERVER_NAME}} — {{SUBTITLE}}

WHAT WE ARE
{{LONG_INTRO_PARAGRAPH}}

GAMEPLAY
• {{FEATURE_1}}
• {{FEATURE_2}}
• {{FEATURE_3}}
• {{FEATURE_4}}
• {{FEATURE_5}}
• {{FEATURE_6}}

VOTING
Vote once per day on each listing site for {{VOTE_REWARD_DESC}}. In-game: {{VOTE_COMMAND}}.

RULES
{{RULES_SUMMARY}}

JOIN
IP: {{SERVER_IP}}
Web: {{WEBSITE}}
Discord: {{DISCORD_INVITE}}
Map: {{MAP_URL}}
Wiki: {{WIKI_OR_RULES_URL}}
```

---

## HTML snippet

```html
<p><strong>{{SERVER_NAME}}</strong> — {{SUBTITLE}}</p>
<ul>
  <li>IP: <strong>{{SERVER_IP}}</strong></li>
  <li>{{FEATURE_1}}</li>
  <li>{{FEATURE_2}}</li>
  <li>Vote: <strong>{{VOTE_REWARD_DESC}}</strong> — {{VOTE_COMMAND}}</li>
  <li><a href="{{WEBSITE}}">Website</a> · <a href="{{DISCORD_INVITE}}">Discord</a></li>
</ul>
```

---

## BBCode

```
[b]{{SERVER_NAME}}[/b] — {{SUBTITLE}}
[list]
[*]IP: [b]{{SERVER_IP}}[/b]
[*]{{FEATURE_1}}
[*]{{FEATURE_2}}
[*]Vote: {{VOTE_REWARD_DESC}}
[*][url={{WEBSITE}}]Website[/url] · [url={{DISCORD_INVITE}}]Discord[/url]
[/list]
```

---

## Tips

- Lead with **IP** or **hook** in the first line — many sites truncate.
- Match **version** to your live jar — wrong version = bad reviews.
- Be honest about **pay-to-win** — players notice quickly.
