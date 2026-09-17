# Telegram Council (Ava → Bruce → Carly)

Machine-local service on the OmniBook. Ollama does personality (Ava Ivy = lead PR / public voice); Python stitches,
classifies, zips artifacts, and gates Cursor Cloud Agents API after `/approve`.

**This is NOT the origin Ava Telegram bot.** It does not use `AVA_TELEGRAM_BOT_TOKEN`
or `apps/core/services/telegram.py` getUpdates. Listener = `TELEGRAM_AVA_TOKEN`
(`@avaivy_bot`) only. Posts also use Bruce/Carly tokens from the same secrets file.

## Secrets

`~/.config/ava-council/secrets.env` (mode 600). Never commit. Keys:

- `TELEGRAM_AVA_TOKEN`, `TELEGRAM_BRUCE_TOKEN`, `TELEGRAM_CARLY_TOKEN`
- `ALEXANDER_TELEGRAM_ID` (optional until `/claim` or username `Alexrs94`)
- `TELEGRAM_GROUP_CHAT_ID` (auto-filled on first group message)
- `CURSOR_API_KEY`, `CURSOR_DEFAULT_REPO`
- optional: `KEEP_ONE_LOADED`, `CHAT_MODEL`, `CLASSIFIER_MODEL`, `AVA_MODEL`, `BRUCE_MODEL`, `CARLY_MODEL`
  (per-voice models still apply when set; Ava stays `qwen2.5:7b-instruct-q4_K_M` by default)
- optional: `TRUST_GAIN_FACTOR` (default 0.5), `TRUST_LOSS_FACTOR` (default 1.5)

State: `~/.config/ava-council/{state,trust}.json`, `runs/`, `mod-log/`.
Never commit `secrets.env`. Backup trust only:

```bash
bash /home/rootrecord/.ollama/skills/origin/scripts/backup-council-trust.sh
```

Copies to `~/Documents/ava-council-backups/` — not secrets.

## Trust ladder

| Score | Label | Meaning |
|------:|-------|---------|
| 0–24 | Watched | Quiet unless @mentioned |
| 25–39 | Guest | Can contribute |
| **40–59** | **Member** | New human joins start here |
| 60–74 | Trusted | May request Cursor implement (owner `/approve`) |
| **75–89** | **Core** | Strong voice; still ≤90 |
| 90 | Peer max | Highest non-owner |
| **100** | **Owner** | Alexander only |

Gains from hidden JUDGE: **+1 → 0.1**, **+2 → 0.2**, **+3 → 0.3**. Same size the other way. Cards fire on whole numbers. Owner `/praise` still uses the old factors. Owner stays 100. Grok Bot `post` does not change trust.

## Owner commands

## Operator setup

1. Add `@avaivy_bot`, `@brucemonitor_bot`, `@carlymal_bot` as **group admins**
   (privacy mode ON — admins still see all messages).
2. Invite link (operator-known): group from BotFather setup.
3. Install user unit (do not enable until ready):

```bash
mkdir -p ~/.config/systemd/user
cp /home/rootrecord/.ollama/skills/origin/apps/council/systemd/ava-council.service \
   ~/.config/systemd/user/ava-council.service
# If .venv missing, edit ExecStart to system python3 -m apps.council
systemctl --user daemon-reload
# Only when TELEGRAM_GROUP_CHAT_ID is set and you want it live:
# systemctl --user enable --now ava-council.service
```

## Entrypoints

From Ava-Core root:

```bash
# prefer venv
./.venv/bin/python -m apps.council --once-status
# or
PYTHONPATH=. python3 -m apps.council --once-status
# or direct
python3 apps/council/run.py --once-status
```

Smoke prints `discussion`, `mode`, `ollama-up`, `owner-bound` — no tokens.

## Owner commands

- `/claim` — bind owner if unset
- `/discussion on|off` · `/holdoff` · `/resume`
- `/mode auto|casual|council`
- `/status` — discussion/mode/ollama + your display name and trust score
- `/approve [id]` · `/reject [id]` · `/implement [id]` — `/approve` with no id uses the latest pending `plan-…`. Only then Cursor runs.
- `/proposals` — list pending plan ids
- `/help` — full slash list (Ava’s Telegram menu only)
- `/trust <user_id|@username> <0-100>` — non-owners clamp to 90
- `/praise @user` · `/warn @user` · `/pardon @user`
- `/name @username Display Name`
- `/autoexecute on|off` (default off; owner-only even when ON; still needs trust ≥95)
- `/cooldown <seconds>` — per-human trigger cooldown (default 90). Owner slash commands and moderation votes are exempt. Bots free. One pipeline at a time always.
- `/debug last` — owner-only: last inbound address decision + job ids (no secrets)
- `/skillrun <id>` — owner-only allowlisted exec skill (currently `council-status`)
- `USER_COOLDOWN_S` in secrets.env

Natural language: “hold off” / “quiet” → holdoff; “resume” / “you can talk” → resume.

## Ollama

Holdoff uses Ava `scripts/idle-stop.sh` when present (else SIGTERM `ollama serve`).
Resume uses `scripts/ensure-ava-runtime.sh` when present (systemctl/nohup serve).
Council does not fight NightOps idle-stop: down Ollama = voices asleep; owner cmds still work.

Models: classifier `qwen2.5:1.5b-instruct-q8_0`; chat default `qwen2.5:7b-instruct-q4_K_M`
with `KEEP_ONE_LOADED=true` (distinct system prompts). Never coder models.

## Cursor

After `/approve` or `/implement`, `POST https://api.cursor.com/v1/agents` with Bearer key, then poll until FINISHED and post the PR URL as Ava.
Empty `CURSOR_API_KEY` → friendly degrade (no local `cursor agent`, no coder models). Non-owners never auto-execute.

## Gitignore

Ignore `__pycache__/` and local `runs/` under this folder. Secrets stay under `~/.config`.


## Grok Bot bridge (manual only)

Telegram auto replies stay on **Ollama**. The Grok Bot teammates (Ava Ivy / Bruce Monitor / Carly Mal)
are **not** woken by Telegram. Alexander speaks through Grok Bot; Grok Bot posts into the group:

```bash
cd /home/rootrecord/.ollama/skills/origin
./.venv/bin/python -m apps.council post --voice ava --text "Hello from Grok Ava"
./.venv/bin/python -m apps.council post --voice bruce --stdin < msg.txt
./.venv/bin/python -m apps.council post --voice ava --file /path/to/bundle.zip --caption "summary"
```

Uses the bound `TELEGRAM_GROUP_CHAT_ID`. No automatic Telegram → Grok Bot trigger.


## Moderation council

Ava leads. On suspected spam/scam (heuristics) or when someone asks to kick/ban/mute (reply to the offending message):

1. Ava posts a proposal  
2. Bruce and Carly vote in-chat  
3. If ≥2 **yes**, Ava executes (`delete` / `mute` / `kick` / `ban`) and applies trust deltas (mute −15, kick −30, ban −50, delete −20; failed vote −2 on heuristic).

Owner, group admins, and other bots are never muted/kicked/banned. Logs: `~/.config/ava-council/mod-log/YYYY-MM-DD.jsonl`.


## Feelings

Each agent keeps dynamic feelings in `~/.config/ava-council/feelings.json` (0–100 axes).
They drift toward baseline, nudge on callouts / dismissal / thanks / spam / holdoff, and are injected into prompts as **one mood word** (never invent metrics or leak secrets). Group chat must not read out axis numbers.

`/feelings` or `/mood` — Ava posts the numeric snapshot.

Addressing is vocative/`@bot` only. Third person (“Ava is the PR agent”) stays quiet. `Thanks team. Bruce, …?` queues Bruce only.

Thought sessions ask questions round-robin (Ava→Bruce→Carly→Ava). They do not write code. Bruce keeps **one daily** `plan-xxxxxxxx.md` and **amends** it. `/done` (or a new HST day) finishes that file; only then does Bruce open a new one. Reply to the file to add notes. Owner `/approve` sends it to Cursor. Carly’s lane is security and performance. Voices read proposal and status files without Cursor. Discord/Slack live hooks are not wired yet.

Telegram slash menu is on **Ava only**. Bruce and Carly have no commands. `/help` prints the same list.

On a **machine reboot** (new kernel boot id), council posts the clean boot/status markdown files (`morning-boot-current.md`, `midday-boot-current.md`, `morning-report-current.md`, `evening-current.md` — not hourly/NWS/solar churn) then queues Ava→Bruce→Carly→Ava to catch up. Mid-session council restarts only arm the boot id and stay quiet. After the first reboot brief, newly generated morning/midday/evening/boot reports also post to the group (Bruce sends files).

Skills live in `apps/council/skills/`. Read skills (glossary, trust ladder, …) inject into the same Ollama turn. The Hawaiian skill pulls matching glosses from the local Wiktionary index — not on every message. Exec skills need owner `/skillrun` (sandbox: `run.py` only, no secrets, no network).
