# Solar day cycles + River out fix (2026-08-03)

Alex: River `out 49W` false; don't mix data cycles; midnight/morning reports then new day.

## Fixes
1. **outW** — trust `pd.wattsOutSum` when present (including 0); do not fall through to ghost `inv.outputWatts`.
2. **inW** — use `pd.wattsInSum` when >0; else max(mppt, inv.input) so River solar intake fills **in**.
3. **Day cycles** — `solarDayCycle.mjs`: HST midnight close + morning open (≥08:00) posts to #updates; minute buckets tagged `hstDay`; morning averages filter prior days.

## Fresh after fix (example)
River: in 54W · out **0W** · solar 54W
