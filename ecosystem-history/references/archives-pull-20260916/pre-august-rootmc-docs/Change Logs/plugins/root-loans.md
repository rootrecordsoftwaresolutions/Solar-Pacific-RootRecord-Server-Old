# root-loans

Personal loans — treasury disbursement, income sweep, gold-ore repayment.

**Source:** `Plugin Building/Minecraft/plugins/root-loans/`

---

## [1.0.10] — 2026-07-31

### Changed
- **`loan-daily-tax.rate`** default **0** (was 0.01 / 1%) — daily loan tax off unless explicitly enabled.

**Deploy:** upload `plugins/RootMC/root-loans.yml`; `/rootloans reload` or restart. Jar optional (code default also 0).

---

## [1.0.9] — 2026-07-02

### Added
- **`loan.max-cap-mode`** — `rank` | `balance` | `min_both` (default **`min_both`**: borrow cap = min(rank limit, wallet balance)).

**Deploy:** `root-loans-1.0.9.jar` + `root-loans.yml` — restart or `/rootloans reload`.

---

## [1.0.8] — 2026-06-29

### Changed
- **Borrow limit = purchased player rank price** — reads tiers from `plugins/RootRecord/root-ranks.yml` via LuckPerms (player track only). Staff/donor groups do not raise the cap.
- **Default cap (no purchased rank):** **100 G** (was 50 G with ×1.1 growth per payoff, hard cap 500 G).
- **LoansConfig:** `default-max-loan`, `rank-limits-enabled`; legacy `starting-max-loan` / `max-loan-multiplier` / `hard-cap` ignored.
- **New:** `RankLoanLimitService`; soft-depend **LuckPerms**.
- **Payoff message:** shows current rank-based limit (limit no longer grows after payoff).
- **MySQL `loans_credit`:** `max_loan` column kept for schema compatibility; limits no longer driven by DB multiplier.

### Config (`root-loans.yml`)
```yaml
loan:
  default-max-loan: 100.0
  rank-limits-enabled: true
```

**Deploy:** `root-loans-1.0.8.jar` + updated `root-loans.yml` → `/rootloans reload` or restart. LuckPerms required for rank caps (falls back to 100 G if missing).

**Wiki:** [Constitution](https://rootmc.net/wiki/constitution/) · [Personal loans](https://rootmc.net/wiki/player/#loans)

---

## [1.0.7] — 2026-06-27 (baseline)

_Changelog tracking started. Prior release history not backfilled._
