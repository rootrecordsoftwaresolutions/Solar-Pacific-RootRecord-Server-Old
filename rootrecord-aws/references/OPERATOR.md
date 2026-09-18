# RootRecord AWS ↔ AVA-CORE — operator handbook

Last verified: **2026-09-17 ~23:40 HST** (live check on this PC + `rr-aws`).

## What is true right now

| Piece | Status |
|-------|--------|
| AVA Console | Open (`launch.sh` in **AVA Console** terminal). Owns the desk. |
| FastFlowLM `:52625` | Starts with console; may restart after heavy NPU use |
| Origin `:8787` | Healthy while console is up |
| Local timers | `rr-ingest.timer` `:10/:25/:40/:55` · `rr-publish.timer` `:00/:15/:30/:45` · `Persistent=true` |
| Offline catch-up | `local/catchup.sh` on console boot + any missed timer slots |
| AWS pollers + packer | Active on `rr-aws` |
| Radio + 65 music beds | `http://127.0.0.1:8000/rootrecord.mp3` on AWS |
| Public radio | Cloudflare quick tunnel URL in AWS `etc/radio-public.url` (also in each zip `sysmon/`) |
| Soft-parked local collectors + play | `OFFLOADED` markers — EcoFlow **not** offloaded |
| AI report briefing | FastFlowLM when console+NPU up; facts-only if FLM down (`RR_PREP_AI=0` forces facts) |

## Clock (HST)

```
AWS pack → Telegram   :10 :25 :40 :55
Local ingest + prep   :10 :25 :40 :55   (Persistent — catches downtime)
Local publish         :00 :15 :30 :45
```

After every successful AWS send: wipe work tree → restart stack (cloudflared skipped so the public URL stays stable).

## Data path (no SSH for data)

1. AWS writes `work/Current.*` + `sysmon/` (logs + free/df/ps).
2. Packer zips non-`.py` files with timestamped names → channel `Root Record Data Relay`.
3. Local `ingest.py` downloads **every** new zip since last offset, archives under `store/archive/<pack_id>/`, sets `store/live/` to newest.
4. `report_prep.py` builds `store/prep/prep-*.md` (facts + optional FLM briefing).
5. `publish.py` archives to `store/published/` on the mark (optional Telegram if `RR_PUBLISH_CHAT_ID` set).

## Chronological drop-ins (AWS FileZilla / SFTP)

```
/home/ubuntu/rootrecord/chronological/
  always-on/*.py
  since-last-fire/*.py
  on-time/*.py
  assets/**          # and any non-.py / non-Current* under chronological/
```

Music beds: `/home/ubuntu/rootrecord/radio/media/` (not wiped). Re-sync from desk:

```bash
bash ~/.ollama/skills/rootrecord-aws/local/sync_music_to_aws.sh
```

## Drag-drop

Logins: `~/Documents/rootrecord-aws-filezilla.env` (chmod 600).

- **SFTP (recommended):** host `3.16.29.76`, port 22, user `ubuntu`, key `~/.ssh/rootrecordkey.pem`
- **FTP:** host `3.16.29.76`, port 21, user `rrftp`, password in that `.env`  
  (Linux uses vsftpd — FileZilla *Client* connects; there is no FileZilla Server package on Ubuntu.)

**AIs:** full server map in `references/AI-SERVER.md`.

## Local commands

```bash
# Deploy AWS code
bash ~/.ollama/skills/rootrecord-aws/aws/deploy.sh

# Reinstall local timers
bash ~/.ollama/skills/rootrecord-aws/local/install-local-timers.sh

# Manual catch-up after long downtime
bash ~/.ollama/skills/rootrecord-aws/local/catchup.sh

# Soft-park collectors / desk play (already done)
bash ~/.ollama/skills/rootrecord-aws/local/soft_park.sh
bash ~/.ollama/skills/rootrecord-aws/local/soft_park_audio.sh
```

Console boot auto-runs catch-up in the background (`launch.sh` → `rr-catchup.log`).

## Secrets (`local/etc/secrets.env`)

| Key | Role |
|-----|------|
| `RR_DATAPACK_RECV_BOT_TOKEN` | Receiver bot only — **exclusive** `getUpdates` for zips |
| `RR_DATAPACK_CHAT_ID` | Relay channel id |
| `RR_TRIGGER_BOT_TOKEN` or `RR_TELEGRAM_BOT_TOKEN` | Reply-now watch — **must differ** from recv (else Telegram 409) |
| `RR_CONTROL_CHAT_ID` | Trigger source chat |
| `RR_PUBLISH_CHAT_ID` | Optional published report destination |

## Soft-parked (no double collectors / no local radio play)

Collectors: `nws-hawaii`, `council-quake`, `earthquake-hourly`, `radar-archive`, `rr-kilauea`, `hurricane-fetch`, `hurricane-tracker`, `rr-noaa`  

Play: `morning-report-play`, `midday-report-play`, `evening-report-play`, `late-report-play`, `evening-report-audio`, `hurricane-radio`, `report-periodic-audio`, `hourly-chime`  

Scheduler skips any skill folder with an `OFFLOADED` file.

## Radio listen

Open the public URL from the latest pack’s `sysmon/radio-public.url`, or:

```bash
ssh -L 8000:127.0.0.1:8000 rr-aws
# http://127.0.0.1:8000/rootrecord.mp3
```

Desk pushes report `*-current.wav` every 5 minutes (`rr-audio-send.timer` →
`local/send_current_wav.py`). On AWS, if no new report arrives within **1.5 hours**,
live reports stop and Ava/Bruce/Carly offline lines in `radio/fallback/` rotate in.

For a permanent hostname: set `RR_CLOUDFLARED_TOKEN` on AWS and restart `rr-cloudflared` once.

## YouTube

Idle until `RR_YOUTUBE_RTMP_URL` is set on AWS secrets.

## Safety

- EcoFlow BLE stays on AVA-CORE.
- Closing AVA Console runs idle-stop — desk processes stop; PC stays on.
- Never commit real bot tokens. If a token appeared in journal logs, rotate it in BotFather.
- Datapath is Telegram zips only — SSH is admin deploy only.
