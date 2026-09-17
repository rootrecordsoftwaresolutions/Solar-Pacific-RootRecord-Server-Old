# Rolled-up concerns

**Snapshot eras:** 2026-08-05 note-keeper rescan + 2026-08-06 failover dump

## Systemic (host / brain)

1. **OptiPlex / ava-core offline** (`192.168.1.62` unreachable) — primary brain missing; laptop failover is temporary
2. **Dream/cloud spend** — keys blanked after ~$5/6h; Discord must not require dream path
3. **Lockout** cleared during failover — verify it stays off unless intentional companion session
4. **dig-health** showed dream `http_403` during dump window
5. **Telegram history** cannot be fully audited via Bot API — operational blind spot (see `_forensics/`)
6. Prior **note-keeper / backend-ops quiet era** intentionally reduced public voice — do not confuse with outage

## Pattern findings (Discord Ava posts, 20260805-050048)

| Pattern | Severity | Count (Aug-5) | Fix |
|---------|----------|---------------|-----|
| dig_theater | high | 20 | Blocked on PROP surfaces + scrub leftovers |
| data_dump (solar/ops) | medium | 46 | Ops dumps private / #updates only |
| long_900+ non-PROP | medium | 28 | Short public cap; PROP long-form only |
| local_core / thin loops | medium | 3 | Sanitize + silence or real answer |
| vendor_leak | medium | 2 | Scrub + delete leftovers |
| unanswered @Ava | medium | 3 | Log; reply only if operator wants |

### Worst rooms (mess score)

general → development → updates → random-facts → Ban boats thread → solar-server

### Systemic causes

- Performing digs instead of answering (instant open/hold + idea-spark congestion)
- Followup-scan catchups into public rooms
- Auto idea-spark on random PROPs (paused)
- Public pack dumps when digs thin
- Earlier purge slightly aggressive (some real long replies may have been removed)

## Telegram thin-loop (critical)

Private TG often *did* send — but degraded into non-answers:

1. Dig-theater openers ("pulling it…", "digging — don't spam me")
2. Pack-inventory essays
3. Terminal thin line: `Still with you - say it another way and I'll answer straight.`

That is **not** healthy Ava. Primary reason Alex perceived "no response."  
Full evidence: `_forensics/telegram-DM-Alex-TRANSCRIPT.md`

## Open @Ava (logged, not auto-replied)

1. **#development · melee5490** — create channel "Ava's progress" + everyday report of what she learned/improved
2. **PROP Quiet hours · rootrecorddev** — stop redundant spam; catch up without multipost
3. **PROP-02 Remove Ava · rootrecorddev** — stop spamming; don't force restarts
