---
name: history
description: >-
  American and Hawaiian history desk. Use when asked about US history, Hawaiʻi
  history, the Hawaiian Kingdom, overthrow, annexation, statehood, Kamehameha,
  Liliʻuokalani, Civil War, Constitution, or related dates. Not ecosystem-history
  (old Ava/OptiPlex paths).
---

# History

Dates and names from the packs below.

Old Ava hosts: `ecosystem-history`. Language: `hawaiian-glossary`. Live volcano
or weather: `weather-kilauea`. Fern Forest lots: `fern-forest`. Stay on this
pack.

## Lookup first

```bash
python3 ~/.ollama/skills/history/scripts/lookup.py overthrow
python3 ~/.ollama/skills/history/scripts/lookup.py --pack hawaiian liliuokalani
python3 ~/.ollama/skills/history/scripts/lookup.py --pack american "civil war"
```

Period asks: read the pack.

- [references/hawaiian.md](references/hawaiian.md)
- [references/american.md](references/american.md)

If both miss, say it is not on this desk. OA papers: Root Record Registry
topic `history`.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic history
```

## Voice

- Date, name, what happened.
- Kahakō and ʻokina on canonical Hawaiian names. ASCII is matching only.
- Visitor copy: short sentences. No repo paths.
- Settlement and population: only ranges already in the pack.
- 1893–1959 Hawaiʻi: Hawaiian pack first.

## Shape

**When** — year or range from the pack.

**What** — one or two sentences.

**Then** — the next beat on the same thread only if it helps.
