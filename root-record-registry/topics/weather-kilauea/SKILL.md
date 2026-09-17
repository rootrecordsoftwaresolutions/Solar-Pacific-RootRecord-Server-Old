---
name: rrr-weather-kilauea
description: >-
  Root Record Registry slice for weather-kilauea. Merges into the live `weather-kilauea` desk.
  OA papers only. Use with registry lookup --topic weather-kilauea.
---

# Registry / weather-kilauea

This is the **paper slice**, not a second weather-kilauea database.
Live ops: `weather-kilauea`. Library: `root-record-registry`.

Volcano/quake papers. Live facts stay on weather-kilauea.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic weather-kilauea
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic weather-kilauea kalo
```

Do not quote a title that lookup did not return. Do not invent a PDF.
