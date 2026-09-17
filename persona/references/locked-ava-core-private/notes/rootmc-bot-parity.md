# RootMC Official vs Ava — bot parity

**Status:** hold Official Discord app + `rootmc-official` jar until this checklist is green **and** Alex signs off.  
**Updated:** 2026-08-02 (Post Absolute Ops soak)

## Do not kick Official until

- [x] Document which automations stay Worker-owned forever (cron reports, board, hourly snapshots, awards, dividends)
- [x] vs which must move to Ava before retire (none required for kick — slash economy + Paper relay stay Official-until-decided)
- [ ] Operator sign-off recorded below

## Ownership map (locked soak)

### Worker-forever (keep Official Discord token / Worker cron — intentional)

| Automation | Module | Notes |
|---|---|---|
| Daily combined + category reports | `rootmc-daily-report-runner.ts`, `rootmc-daily-combined.ts`, `rootmc-daily-category-reports.ts` | Scheduled via `realm-index.ts` |
| Weekly intelligence suite | `rootmc-weekly-report-runner.ts` / `rootmc-weekly-reports.ts` | Sunday HST slot |
| Weekly activity awards | `rootmc-weekly-activity-awards.ts` | Bot exclusion + claim-before-post deployed Absolute Ops |
| Monthly Activity Dividend | `rootmc-treasury.ts` (`runMonthlyTreasuryDividendCron`) | Treasury debit, not wallet mint |
| `#automated-reports` board | `rootmc-automated-reports-board.ts` | Hourly refresh + after weekly |
| Live economy / hourly status | `rootmc-live-economy-status.ts` | Pending-proposals block decision stays Worker |
| Slack `#server-reports` mirror | report runners | Staff mirror — not Ava |
| Legislature / proposal expire crons | `rootmc-legislature.ts`, `rootmc-community-proposals.ts` | Worker governance timers |

### Official-until-decided (Discord slash / interactions — do not retire yet)

| Surface | Owner today | Notes |
|---|---|---|
| `/help` `/link` `/server` | Worker `discord-rootmc-bot.ts` | No Ava slash parity yet — chat redirects OK |
| `/balance` `/pay` `/value`/`worth` | Worker Official | Gold economy — keep Official or deliberate Ava chat policy later |
| `/vote` power + poll links | Worker Official | Ava owns **text** ballots in `#voting`; slash summary stays Official |
| `/proposal` Discord slash | Worker Official | Ava drains **in-game** `/proposal` queue (`proposalIdeas.mjs`) |
| Timezone role select | Worker Official | Keep or drop later — not Ava |
| Proposal buttons | Worker Official | Ava text votes are source of truth; buttons ignored/retired when safe |

### Paper `rootmc-official` jar (hold)

| Capability | Path | Decision |
|---|---|---|
| Hourly Paper log relay → Slack `#server-logs` | `OfficialHourlyLogRelay.java` | Stay on Official jar for now (or move to Root-Core later — DECISION) |
| Progression sync | `rootmc-official` plugin | Stay |
| Chat bridge | Root-Core (not Official Discord app) | Unchanged |
| `/report` ticket channels | Worker + plugin | Stay |

### Ava already owns (not blockers for kick — already live)

| Area | Paths |
|---|---|
| Proposal formalize | `Web Files/rootmc-ava/src/proposalIdeas.mjs` |
| Feedback drain | `feedbackInbox.mjs` |
| Text votes | `voteText.mjs` |
| Poll seed / Ava ballot | `pollWatcher.mjs` + `seedVoteReactions.mjs` |
| Multipost style | `splitContent.mjs` / `avaPost.mjs` |
| Onboarding DM | `onboarding.mjs` |
| RSS → updates | `rssWatch.mjs` |
| Channel cleanup (operators) | `channelCleanup.mjs` |

## Governance (Ava-led — verify live)

- [x] In-game `/proposal` queue drains while Ava online (+ offline catch-up on boot) — `proposalIdeas.mjs`
- [x] Formal PROP + #proposals thread + poll appear without Official slash — API formalize
- [x] #voting text votes record weighted ballots — `voteText.mjs`
- [x] Poll watcher seeds ✅/❌/➖ and Ava auto-vote — `pollWatcher.mjs` / `seedVoteReactions.mjs`
- [x] `/feedback` queue acked; Slack #feedback still staff copy — `feedbackInbox.mjs`
- [ ] Buttons retired or ignored; no double-count with text (still Official interactions path)

## Player Discord commands (Official today)

- [x] Documented as Official-until-decided (table above) — no Ava slash surface this soak
- [ ] `/help` `/link` `/server` parity or deliberate Ava chat equivalents — deferred
- [ ] `/balance` `/pay` `/value`|/worth — keep Official until policy
- [ ] `/vote` power summary + poll links — keep Official
- [ ] `/proposal` Discord slash vs in-game-only — keep dual until policy
- [ ] Timezone role select — keep Official or drop

## Reports & board (Worker today — intentional keep)

- [x] Daily combined + economy/towns/nations still post (`rootmc-daily-*.ts`) — Worker-forever
- [x] Weekly suite + activity awards — Worker-forever
- [x] Monthly Activity Dividend — Worker-forever
- [x] `#automated-reports` board refreshes — Worker-forever
- [x] Slack `#server-reports` mirror still fires — Worker-forever
- [x] Hourly snapshots: pending-proposals Governance block (`rootmc-live-economy-status.ts`) — Worker-forever; `job-ms9s7pa0` done

## Host / logs

- [x] Hourly Paper log relay (`OfficialHourlyLogRelay`) → Slack `#server-logs` — stay Official jar
- [x] Chat bridge stays Root-Core (not Official Discord app)
- [x] `/report` ticket channels remain Worker+plugin

## Retire gate

- [ ] Ava online ≥ N days covering proposal+feedback+votes without Official governance regressions
- [ ] Operator sign-off on Worker-kept cron list (this doc)
- [ ] Only then remove/disable Official Discord application from guild

**Operator sign-off:** _pending — do not kick_

## Absorb plan (next, not this soak)

1. Keep Worker cron + board on Official token (infrastructure) — **documented**.
2. Slash economy remains “Official until Ava command surface or chat-only policy.”
3. Paper hourly log relay stays on `rootmc-official` or moves to Root-Core — DECISION later.
4. After checklist green + Alex sign-off → update urgent registry `ops-legacy-bot` → done + announce retire window.

## Absolute Ops + Post Absolute Ops soak

- Confirmed Ava-owned: proposalIdeas, feedbackInbox, voteText, pollWatcher, seedVoteReactions.
- Worker awards deploy landed (bot exclusion + claim-before-post) — weekly awards stay Worker-owned intentionally.
- Slash economy + timezone selects remain Official until deliberate Ava command surface.
- Ownership tables filled 2026-08-02 Post Absolute Ops soak.
- Still **do not kick** Official.
