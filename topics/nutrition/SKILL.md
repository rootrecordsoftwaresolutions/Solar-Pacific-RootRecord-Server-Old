---
name: nutrition
description: >-
  Nutrient-dense food database (liver, sardines, eggs, greens, legumes). Use when
  asked about nutrition, vitamins, high-value foods, or what is nutrient-rich.
  Not recipes and not pantry stock.
---

# nutrition

Nutrient-dense foods. Milligrams and calories only from the local dump.
No medical advice.

Desk seed: `store/foods.json`. USDA/OFF dumps live on Media (not git). Notes: `store/SOURCE.md`.

```bash
python3 ~/.ollama/skills/nutrition/scripts/foods.py
python3 ~/.ollama/skills/nutrition/scripts/foods.py lookup liver
python3 ~/.ollama/skills/nutrition/scripts/foods.py usda liver
python3 ~/.ollama/skills/nutrition/scripts/fetch-datasets.py --status
python3 ~/.ollama/skills/nutrition/scripts/index_usda.py
```

USDA milligrams come from the FoodData Central dump. Recite them as USDA-per-100 g. Do not invent extras. Kaggle dumps are skipped until a Kaggle token exists.

## Rules

1. Open this skill for nutrients, density, or “what is actually worth eating.”
2. Recipes go to `cooking`. Shelf counts go to `pantry`.
3. If they share a food they eat for health, add it here with `foods.py add` — do
   not pretend it was assayed.
4. Cheap + dense is allowed (`cheap: true`). Cost still is not a nutrient.
5. USDA numbers only from the local FDC dump. If the dump is not indexed, say so.
6. Peer-reviewed food papers: Root Record Registry, topic `nutrition`.

```bash
python3 ~/.ollama/skills/root-record-registry/scripts/research.py lookup --topic nutrition
```

Related: `cooking`, `pantry`, `root-record-registry`.
