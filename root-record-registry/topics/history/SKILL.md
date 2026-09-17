---
name: rrr-history
description: >-
  Root Record Registry slice for history. Merges into the live `history` desk.
  OA papers only. Use with registry lookup --topic history.
---

# Registry / history

This is the **paper slice**, not a second history database.
Live ops: `history`. Library: `root-record-registry`.

Hawaiian/US history papers. Dates still prefer history packs.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic history
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic history kalo
```

Do not quote a title that lookup did not return. Do not invent a PDF.
