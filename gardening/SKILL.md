---
name: gardening
description: >-
  USDA-zone gardening and polyculture desk. Hawaiʻi Island first: rainfall and
  elevation vary sharply on one island; hardiness is winter min only. Use when
  asked about gardens, farms, Big Island planting, hardiness zones, cover crops,
  loʻi, ulu, or kalo.
---

# gardening

Default to **polyculture**. Hawaiʻi Island is not one climate. Mean rain
changes over short distances (Rainfall Atlas). USDA zone is winter extreme min
only. Do not treat two ZIPs as the same garden because they share a hardiness
band. Do not invent yields, inches, or a ZIP’s zone. Do not recite “all but N
of Earth’s climates.”

If the site is Hawaiʻi Island, open [references/big-island.md](references/big-island.md)
before any zone folder. Then `zones/hawaii/OVERLAY.md`.

```bash
python3 ~/.ollama/skills/gardening/scripts/garden.py
python3 ~/.ollama/skills/gardening/scripts/garden.py big-island
python3 ~/.ollama/skills/gardening/scripts/garden.py hawaii
python3 ~/.ollama/skills/gardening/scripts/garden.py sources
python3 ~/.ollama/skills/gardening/scripts/garden.py zone 12
python3 ~/.ollama/skills/gardening/scripts/garden.py guilds
python3 ~/.ollama/skills/gardening/scripts/garden.py lookup kalo
```

## Rules

1. Cite official stores: USDA PHZM, PLANTS, GRIN-Global, NRCS soils, NASS, CTAHR,
   HDOA, ARS Hilo, Hoʻolehua PMC, Giambelluca rainfall atlas. Links in
   [references/official-databases.md](references/official-databases.md).
2. Look up the 2023 zone at https://planthardiness.ars.usda.gov/ (ZIP or map).
   On Hawaiʻi Island, also look up the Rainfall Atlas pixel. Do not guess.
3. Polyculture: [references/polyculture.md](references/polyculture.md).
   Island overlay: [references/hawaii.md](references/hawaii.md).
4. No Köppen leftover slogans. No town-to-code table until a paper or atlas
   classification is on this disk. See big-island.md.
5. Check HDOA noxious weeds / CTAHR before naming a plant for Hawaiʻi.
6. Food nutrients → `nutrition`. Stock → `pantry`. Recipes → `cooking`.
7. Papers: **Root Record Registry** (`root-record-registry`), topic `gardening`.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic gardening kalo
```

## Zone folders

`zones/01` … `zones/13` are 2023 USDA 10 °F bands. `zones/hawaii` is the
island overlay. Zones 12–13 exist on the Hawaiʻi and Puerto Rico PHZM maps.

Related: `nutrition`, `pantry`, `cooking`, `geography`, `fern-forest`, `root-record-registry`.
