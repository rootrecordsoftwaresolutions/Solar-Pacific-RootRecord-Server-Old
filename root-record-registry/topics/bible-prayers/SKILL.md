---
name: rrr-bible-prayers
description: >-
  Root Record Registry slice for bible-prayers. Merges into the live `bible-prayers` desk.
  OA papers only. Use with registry lookup --topic bible-prayers.
---

# Registry / bible-prayers

This is the **paper slice**, not a second bible-prayers database.
Live ops: `bible-prayers`. Library: `root-record-registry`.

Theology OA if tagged. Scripture lookup stays WEBU on bible-prayers.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic bible-prayers
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic bible-prayers kalo
```

Do not quote a title that lookup did not return. Do not invent a PDF.
