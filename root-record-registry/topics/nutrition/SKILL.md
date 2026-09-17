---
name: rrr-nutrition
description: >-
  Root Record Registry slice for nutrition. Merges into the live `nutrition` desk.
  OA papers only. Use with registry lookup --topic nutrition.
---

# Registry / nutrition

This is the **paper slice**, not a second nutrition database.
Live ops: `nutrition`. Library: `root-record-registry`.

Nutrient papers only. Milligrams still come from the FDC dump.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic nutrition
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic nutrition kalo
```

Do not quote a title that lookup did not return. Do not invent a PDF.
