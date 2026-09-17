---
name: web-facts
description: >-
  Allowlisted HTTPS GET for council (NWS, USGS, Wikipedia, Litecoin docs).
  Use when they need a live public fact. Not a general browser.
---

# web-facts

Allowlist only. No cookies. No JS. Does not write live skills.

Hosts: `api.weather.gov`, `earthquake.usgs.gov`, `en.wikipedia.org`,
`lite.wikipedia.org`, `docs.litecoin.org`, `download.litecoin.org`.

Draft new skills under `goals/store/skill-ideas/`. Owner/Cursor promotes.

```bash
python3 ~/.ollama/skills/web-facts/scripts/web_facts.py 'https://api.weather.gov/'
```
