---
name: rrr-pantry
description: >-
  Root Record Registry slice for pantry. Merges into the live `pantry` desk.
  OA papers only. Use with registry lookup --topic pantry.
---

# Registry / pantry

This is the **paper slice**, not a second pantry database.
Live ops: `pantry`. Library: `root-record-registry`.

Food-stock papers if any. Counts stay in pantry stock.json.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic pantry
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic pantry kalo
```

Do not quote a title that lookup did not return. Do not invent a PDF.
