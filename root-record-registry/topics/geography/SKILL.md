---
name: rrr-geography
description: >-
  Root Record Registry slice for geography. Merges into the live `geography` desk.
  OA papers only. Use with registry lookup --topic geography.
---

# Registry / geography

This is the **paper slice**, not a second geography database.
Live ops: `geography`. Library: `root-record-registry`.

Rain, orographic, place. Atlas still beats a paper for inches.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic geography
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic geography kalo
```

Do not quote a title that lookup did not return. Do not invent a PDF.
