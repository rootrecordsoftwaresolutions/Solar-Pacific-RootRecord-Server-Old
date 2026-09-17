---
name: rrr-cooking
description: >-
  Root Record Registry slice for cooking. Merges into the live `cooking` desk.
  OA papers only. Use with registry lookup --topic cooking.
---

# Registry / cooking

This is the **paper slice**, not a second cooking database.
Live ops: `cooking`. Library: `root-record-registry`.

Recipe literature. User dishes still save on the cooking desk.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic cooking
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic cooking kalo
```

Do not quote a title that lookup did not return. Do not invent a PDF.
