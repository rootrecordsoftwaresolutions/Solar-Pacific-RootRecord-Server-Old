---
name: root-record-registry
description: >-
  Root Record Registry: UH ScholarSpace and other legal OA papers, tagged onto
  existing desks (gardening, nutrition, history, geography). Use when Bruce needs
  a thesis, CTAHR report, DOI, or OA PDF. Not paywalled journals. Not medical advice.
---

# Root Record Registry

This is the **paper library**. Topic desks stay themselves. They read
**their slice** here. Do not quote a paper that is not in the catalog. Do not
fetch a PDF unless it is OA. Preprints are preprints. No medical advice.

Hour harvest (run in a terminal; resumes if you stop it):

```bash
PYTHONUNBUFFERED=1 python3 -u ~/.ollama/skills/root-record-registry/scripts/harvest-hour.py --minutes 60
```

Philosophy harvest (own state file; UH Philosophy + Religion, then OA):

```bash
PYTHONUNBUFFERED=1 python3 -u ~/.ollama/skills/root-record-registry/scripts/harvest-philosophy.py --minutes 60
```

Background:

```bash
mkdir -p ~/.ollama/skills/root-record-registry/store
PYTHONUNBUFFERED=1 nohup python3 -u ~/.ollama/skills/root-record-registry/scripts/harvest-hour.py --minutes 60 >> ~/.ollama/skills/root-record-registry/store/harvest.log 2>&1 &
echo $!
tail -f ~/.ollama/skills/root-record-registry/store/harvest.log
```

Background philosophy:

```bash
mkdir -p ~/.ollama/skills/root-record-registry/store
PYTHONUNBUFFERED=1 nohup python3 -u ~/.ollama/skills/root-record-registry/scripts/harvest-philosophy.py --minutes 60 >> ~/.ollama/skills/root-record-registry/store/harvest-philosophy.log 2>&1 &
echo $!
tail -f ~/.ollama/skills/root-record-registry/store/harvest-philosophy.log
```

It downloads ScholarSpace CTAHR + OpenAlex OA + Europe PMC OA, saves PDFs,
tags them, and hardlinks into `Media/public/documents/research-oa/by-topic/<desk>/`.
State: `store/harvest-state.json`. Stops if free disk drops under 8 GiB.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py status
python3 ~/.ollama/skills/root-record-registry/scripts/research.py tag
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic gardening kalo
python3 ~/.ollama/skills/root-record-registry/scripts/research.py harvest-ctahr
python3 ~/.ollama/skills/root-record-registry/scripts/research.py harvest-openalex
python3 ~/.ollama/skills/root-record-registry/scripts/research.py pull-pdfs 80
python3 ~/.ollama/skills/root-record-registry/scripts/research.py organize
```

Dumps: `/home/rootrecord/Media/public/documents/research-oa/` (PDFs). Catalog:
`store/catalog.json`.

## Topic desks (already laid out)

| Slice | Live skill | Registry folder |
| --- | --- | --- |
| Plants, polyculture, CTAHR | `gardening` | [topics/gardening/SKILL.md](topics/gardening/SKILL.md) |
| Nutrient assays | `nutrition` | [topics/nutrition/SKILL.md](topics/nutrition/SKILL.md) |
| Recipes | `cooking` | [topics/cooking/SKILL.md](topics/cooking/SKILL.md) |
| Shelf counts | `pantry` | [topics/pantry/SKILL.md](topics/pantry/SKILL.md) |
| Hawaiian / US history | `history` | [topics/history/SKILL.md](topics/history/SKILL.md) |
| Place names / rain | `geography` | [topics/geography/SKILL.md](topics/geography/SKILL.md) |
| Fern Forest lots | `fern-forest` | [topics/fern-forest/SKILL.md](topics/fern-forest/SKILL.md) |
| Scripture | `bible-prayers` | [topics/bible-prayers/SKILL.md](topics/bible-prayers/SKILL.md) |
| Volcano / quakes | `weather-kilauea` | [topics/weather-kilauea/SKILL.md](topics/weather-kilauea/SKILL.md) |
| Ethics, epistemology, Hawaiian thought | `philosophy` | [topics/philosophy/SKILL.md](topics/philosophy/SKILL.md) |

A record can wear more than one tag. Planting still uses the Rainfall Atlas on
`gardening`, not a paper’s vibe.

Stores: [references/sources.md](references/sources.md). Old name `research-oa` redirects here.
