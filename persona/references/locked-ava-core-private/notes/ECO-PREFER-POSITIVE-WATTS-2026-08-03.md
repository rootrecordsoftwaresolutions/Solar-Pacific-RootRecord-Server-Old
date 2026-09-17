# EcoFlow in/out zero-skip (2026-08-03)

River showed `solar 54W` / `in 0W` because `pickPowerWatts` took the first *present* key (`inv.inputWatts=0`) before `mppt.inWatts=54`.

**Fix:** `getPreferPositive` — skip zero placeholders; fall back to zero only if all candidates are zero.

Same for out (`pd.wattsOutSum=0` vs `inv.outputWatts`).
