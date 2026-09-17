# Master fields (template)

Fill [`templates/server-profile.example.md`](../templates/server-profile.example.md) once, then replace every `{{...}}` below when pasting.

---

## Identity

| Field | Template |
|-------|----------|
| **Server name** | {{SERVER_NAME}} |
| **Network name** | {{NETWORK_NAME}} |
| **Tagline** | {{TAGLINE}} |
| **Short subtitle** | {{SUBTITLE}} |

---

## Connection

| Field | Template |
|-------|----------|
| **Java IP** | {{SERVER_IP}} |
| **Port** | {{PORT}} |
| **Bedrock** | {{BEDROCK_YES_NO}} |
| **Version** | {{MINECRAFT_VERSION}} |
| **Software** | {{PAPER_SPIGOT_FABRIC}} |
| **Max players** | {{MAX_PLAYERS}} |
| **Whitelist** | {{WHITELIST_YES_NO}} |
| **Cracked** | {{CRACKED_YES_NO}} |

---

## Links

| Field | Template |
|-------|----------|
| **Website** | {{WEBSITE}} |
| **Discord** | {{DISCORD_INVITE}} |
| **Map** | {{MAP_URL}} |
| **Wiki / rules** | {{WIKI_OR_RULES_URL}} |
| **Store** | {{STORE_URL}} |
| **Trailer** | {{TRAILER_URL}} |

---

## Standard listing row

Use on most add-server forms:

| Field | Paste |
|-------|-------|
| Name | {{SERVER_NAME}} |
| IP | {{SERVER_IP}} |
| Port | {{PORT}} |
| Website | {{WEBSITE}} |
| Discord | {{DISCORD_INVITE}} |
| Description | Medium from [`descriptions.md`](descriptions.md) |
| Version | {{MINECRAFT_VERSION}} |

---

## Feature bullets (customize 5–8)

- {{FEATURE_1}}
- {{FEATURE_2}}
- {{FEATURE_3}}
- {{FEATURE_4}}
- {{FEATURE_5}}
- {{FEATURE_6}}
- {{FEATURE_7}}
- {{FEATURE_8}}

**Examples owners often use:**

- Custom economy / player shops / auction house
- Land claim / Towny / Factions / grief protection
- McMMO, jobs, or skill progression
- Live dynmap / BlueMap / squaremap
- Discord ↔ in-game chat bridge
- Weekly events and active staff
- No pay-to-win / EULA-friendly store
- Bedrock crossplay (Geyser) — only if true

---

## Voting

| Field | Template |
|-------|----------|
| **Vote command** | {{VOTE_COMMAND}} |
| **Reward text** | {{VOTE_REWARD_DESC}} |
| **Votifier host** | {{VOTIFIER_HOST}} |
| **Votifier port** | {{VOTIFIER_PORT}} |

Add per-site vote URLs to your local profile as you register.

---

## Contact

| Field | Template |
|-------|----------|
| **Owner** | {{OWNER_NAME}} |
| **Email** | {{EMAIL}} |
| **Region** | {{REGION}} |
| **Language** | {{LANGUAGE}} |

---

## Media

| Asset | Notes |
|-------|-------|
| **Banner** | 468×60 GIF or PNG — same file on every site |
| **Screenshots** | 3–6: spawn, gameplay, events, builds |
| **Icon** | Server logo or stylized text |

---

## Gamemode checkboxes

See [`tags-and-categories.md`](tags-and-categories.md) — only select modes you actually run.
