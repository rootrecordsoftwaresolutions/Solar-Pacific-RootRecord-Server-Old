---
name: rrr-philosophy
description: >-
  Root Record Registry slice for philosophy. Merges into the live `philosophy` desk.
  OA papers only. Use with registry lookup --topic philosophy.
---

# Registry / philosophy

This is the **paper slice**, not a second philosophy lecture.
Live ops: `philosophy`. Library: `root-record-registry`.

UH Philosophy/Religion IR plus OA ethics, epistemology, Hawaiian thought.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic philosophy
python3 ~/.ollama/skills/root-record-registry/scripts/harvest-philosophy.py --minutes 60
```

Do not quote a title that lookup did not return. Do not invent a PDF.
