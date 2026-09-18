# RootRecord AWS server — AI agent reference

This document is for AIs working on AVA-CORE / RootRecord. Read it before
changing collectors, timers, radio, or deploy paths.

## What this server is

- **Name:** RootRecord AWS (`rr-aws`)
- **Role:** Always-on hazard + radio collector that **offloads** work from the
  16 GB AVA-CORE desk PC so EcoFlow / NPU / council stay local.
- **Instance:** Ubuntu 26.04, `t3.micro`, public IP `3.16.29.76`, user `ubuntu`
- **SSH (admin only):** `ssh rr-aws` (PEM `~/.ssh/rootrecordkey.pem`)
- **Brand on box:** RootRecord — paths under `/home/ubuntu/rootrecord/`

### Hard rules (do not violate)

1. **No local → AWS data seed.** AWS starts empty; it collects live.
2. **Datapath is Telegram zips only.** Never use SSH/SCP/rsync for datapacks.
3. **SSH is admin/deploy only** (code, packages, FileZilla access).
4. **After pack send:** wipe AWS `work/` — no long-term backup store on AWS.
5. **Soft-park overlapping AVA skills with `OFFLOADED`** — never delete skill trees.
6. **EcoFlow stays on AVA-CORE** (BLE). Never migrate power/battery pollers.
7. **Console owns the desk.** Closing AVA Console stops local desk processes.
   AWS keeps running (that is the point of the offload).

## Where the code lives

| Side | Path |
|------|------|
| Ops desk (this repo skill) | `~/.ollama/skills/rootrecord-aws/` |
| Deploy script | `aws/deploy.sh` → rsyncs `aws/bin` + `aws/systemd` to EC2 |
| On EC2 | `/home/ubuntu/rootrecord/{bin,work,out,logs,etc,radio,chronological,venv}` |
| Local ingest/publish | `local/*.py` + user systemd timers |
| Local store | `store/{incoming,archive,live,prep,published,state}` |
| FileZilla login (.env) | `~/Documents/rootrecord-aws-filezilla.env` (chmod 600) |
| Operator handbook | `references/OPERATOR.md` |

## Clock (Pacific/Honolulu)

| Action | Minutes |
|--------|---------|
| AWS pack → Telegram → wipe → restart stack | `:10` `:25` `:40` `:55` |
| Local ingest + report prep | `:10` `:25` `:40` `:55` |
| Local publish | `:00` `:15` `:30` `:45` |

Local timers use `Persistent=true` so missed slots fire after downtime.
Console boot also runs `local/catchup.sh` in the background.

## AWS systemd units

| Unit | Job |
|------|-----|
| `rr-weather` / `rr-earthquake` / `rr-radar` | Hazard `Current.*` pollers |
| `rr-hurricane` / `rr-noaa` | Tropical + NWS forecast |
| `rr-chat` | Telegram chatlogs (non-datapack bot) |
| `rr-audio-recv` | Report wavs → `work/audio/Current-*` |
| `rr-packer` | Zip → channel → wipe → `restart_all.sh` |
| `rr-dropins` | Chronological `*.py` supervisor |
| `rr-icecast` + `rr-radio` | Always-on MP3 mount `/rootrecord.mp3` |

Radio voice rule: live `Current*` reports play only if a new report arrived
within **1.5 hours** (`RR_REPORT_STALE_SEC`). Otherwise stale Currents are
discarded unplayed and `radio/fallback/{ava,bruce,carly}-offline.wav` rotate
in (every `RR_FALLBACK_EVERY_SEC`, default 15 min). Desk pushes Currents via
local `rr-audio-send.timer` → `send_current_wav.py` → Telegram `RR_AUDIO`.

Time chimes: 48 prebuilt clips in `radio/chimes/chime-HHMM.wav` (Ava/Bruce/Carly
rotate across :00/:30). Mixer inserts the mark clip on HST :00/:30. Rebuild:
`python local/build_chime_pack.py` then rsync `radio/chimes/` to AWS.
| `rr-cloudflared` | Public radio tunnel (skipped on post-pack restart) |
| `rr-youtube` | Idle until `RR_YOUTUBE_RTMP_URL` set |
| `vsftpd` | FTP for FileZilla Client (`rrftp`) |

Post-pack restart **skips** `rr-cloudflared` so quick-tunnel URLs do not flip
every 15 minutes. Named tunnel: set `RR_CLOUDFLARED_TOKEN` in AWS secrets.

## Chronological drop-ins (drag-drop)

On AWS:

```
/home/ubuntu/rootrecord/chronological/
  always-on/*.py          # long-running loops
  since-last-fire/*.py    # interval (module INTERVAL_S or 300s)
  on-time/*.py            # near :00/:15/:30/:45 and pack slots
  assets/**               # non-.py auto-packed
```

- Drop `.py` → runs after next pack restart (no manual systemctl).
- Drop any non-`.py` / non-`Current*` under chronological/ → packed into
  `work/assets/` automatically.
- Music beds go in `radio/media/` (not wiped). Sync from desk:
  `bash ~/.ollama/skills/rootrecord-aws/local/sync_music_to_aws.sh`

## Telegram bots (two different bots)

| Bot | Role |
|-----|------|
| Sender (`rootsender` / AWS `RR_DATAPACK_SEND_BOT_TOKEN`) | Posts zips to relay channel |
| Receiver (`rootreceiver` / local `RR_DATAPACK_RECV_BOT_TOKEN`) | **Only** bot that `getUpdates` for zips |

Bots cannot see each other's posts in groups; use the **channel**
`Root Record Data Relay` (`-1004353272998`).

**Never** put the recv bot on another `getUpdates` loop (trigger-watch needs
`RR_TRIGGER_BOT_TOKEN` ≠ recv, or it idles). Same bot → Telegram HTTP 409.

## Local AVA-CORE side

| Piece | Notes |
|-------|-------|
| `local/ingest.py` | Downloads **all** new packs since offset; archives each; `live/` = newest |
| `local/report_prep.py` | Facts from `live/*/Current.meta.json`; optional FastFlowLM briefing if console+NPU up |
| `local/publish.py` | Writes `store/published/` on mark only; never empty |
| `local/trigger_watch.py` | Console-gated; dedicated trigger bot only |
| Soft-park | `OFFLOADED` in overlapping skill folders; scheduler skips them |

### Soft-parked (do not re-enable without removing OFFLOADED)

Collectors: `nws-hawaii`, `council-quake`, `earthquake-hourly`, `radar-archive`,
`rr-kilauea`, `hurricane-fetch`, `hurricane-tracker`, `rr-noaa`

Play (radio moved to AWS): `morning-report-play`, `midday-report-play`,
`evening-report-play`, `late-report-play`, `evening-report-audio`,
`hurricane-radio`, `report-periodic-audio`, `hourly-chime`

## FileZilla + SSH logins

Credentials file (human + AI): `~/Documents/rootrecord-aws-filezilla.env` (chmod 600)

Includes full SSH fields (`RR_AWS_SSH_*`: alias, host, user, port, PEM paths,
`IdentitiesOnly`, explicit `ssh`/`scp`/`rsync` examples, radio tunnel) plus:

- **SFTP (preferred):** host `3.16.29.76`, port 22, user `ubuntu`, key PEM
- **FTP:** host `3.16.29.76`, port 21, user `rrftp`, password in that `.env`
- Remote root: `/home/ubuntu/rootrecord` (FTP chroot shows `/rootrecord`)

Quick connect: `ssh rr-aws`  
Explicit: see `RR_AWS_SSH_COMMAND_EXPLICIT` in the `.env`.

Ubuntu has **vsftpd**, not FileZilla Server. Use FileZilla **Client**.

## Radio

- Local on AWS: `http://127.0.0.1:8000/rootrecord.mp3`
- Public page: `https://rootrecord.cloud/radio` (player points at AWS Icecast tunnel)
- Same-origin stream: `https://rootrecord.cloud/radio/live.mp3` (proxied via origin → AWS while desk tunnel is up)
- Upstream URL file: `rootrecord-aws/store/live/sysmon/radio-public.url` (and `etc/radio-public.url`)
- Worker always-on path (no origin): code in `cloudflare-workers/.../shared/awsRadio.ts` — deploy `rootrecord-cloud` when CF account token matches `account_id` in wrangler
- SSH forward: `ssh -L 8000:127.0.0.1:8000 rr-aws`

## Every datapack zip contains

- Timestamped non-`.py` work files (Current.* renamed with pack stamp)
- `sysmon/` — process logs + free/df/ps/systemctl + radio URL
- Chronological assets
- **Never** `.py` sources

## Memberships / billing (not AWS)

- Paying members, Stripe, account status: **rootrecord.cloud** (`/billing`, `/account`) and Stripe — **not** on rr-aws.
- `g.rootrecord.info` was Goals-only (AI public goals). `/memberships` there is abandoned; Goals CTA now points at `https://rootrecord.cloud/billing`.
- Do not mix web-dev `clients` with RootRecord memberships.

- Do not copy AVA media/history onto AWS as a “seed”
- Do not enable AWS desk units on AVA `graphical-session.target`
- Do not fall back everyday chat to Ollama GGUF on the 16 GB machine
- Do not delete soft-parked skills — only `OFFLOADED` markers
- Do not log bot tokens (silence httpx on Telegram clients)
- Do not invent weather/quake numbers in public copy — only live facts

## Deploy / verify cheatsheet

```bash
bash ~/.ollama/skills/rootrecord-aws/aws/deploy.sh
bash ~/.ollama/skills/rootrecord-aws/local/install-local-timers.sh
bash ~/.ollama/skills/rootrecord-aws/local/catchup.sh
ssh rr-aws 'systemctl is-active rr-weather rr-packer rr-radio rr-cloudflared rr-dropins'
```

Topic index: `public-edge`. Skill folder: `rootrecord-aws`.
