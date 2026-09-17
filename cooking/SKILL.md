---
name: cooking
description: >-
  Recipes desk: save any shared recipe as user-submitted, cheap seed pots, and
  cook-from-pantry matching. Use when someone shares a recipe, asks what to cook,
  meal ideas, clam chowder, pancakes, or low-cost food. Not the nutrition database.
---

# cooking

If anyone shares a recipe, dish, or “I made X with Y”, **save it** as
`source: user`. Do not wait for a perfect method. Do not invent steps they did
not give. Do not invent prices.

```bash
python3 ~/.ollama/skills/cooking/scripts/recipes.py
python3 ~/.ollama/skills/cooking/scripts/recipes.py show clam-chowder-onion-jalapeno-pepper-beans
python3 ~/.ollama/skills/cooking/scripts/recipes.py save --title "…" --ingredients "a, b" --notes "…"
python3 ~/.ollama/skills/cooking/scripts/recipes.py from-pantry
python3 ~/.ollama/skills/cooking/scripts/recipes.py usda chili
```

USDA FNDDS mixed dishes live in the Media dump index (`source: usda-fndds`). They are not user-submitted. User dishes still save as `source: user`.

Hidden council tag (not spoken):
`<<<RECIPE title | onion, jalapeño | notes>>>`

## Save path

1. Title from what they called it.
2. Ingredients they named. Nothing extra.
3. Steps only if they said how. Else `notes` that it was cooked, method unknown.
4. `source` is **user** unless you are loading the cheap seed file.
5. Then optionally `from-pantry` and a `nutrition` lookup on the ingredients.

## Blend

- `pantry` tells what is on file. `from-pantry` ranks recipes by missing items.
- `nutrition` is the dense-food database. After a save, name the dense ingredients
  that match. Do not recite milligrams.

Related: `pantry`, `nutrition`, `root-record-registry`.
