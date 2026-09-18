# RootRecord AWS operator notes

## Host
- SSH: `ssh rr-aws` (admin only — never for datapacks)
- Tree: `/home/ubuntu/rootrecord/`
- Relay channel: `Root Record Data Relay` `-1004353272998` (`https://t.me/c/4353272998/`)

## What runs on AWS
| Service | Role |
|---------|------|
| rr-weather / rr-earthquake / rr-radar | Hazard Current.* pollers |
| rr-hurricane / rr-noaa | Tropical + NWS forecast |
| rr-packer | Zip → Telegram → wipe → **restart all** at :10/:25/:40/:55 HST |
| rr-dropins | Chronological `*.py` drop-ins + auto-pack assets |
| rr-audio-recv | Local current_*.wav → work/audio/Current-* |
| rr-icecast + rr-radio | Always-on MP3 mount `/rootrecord.mp3` |
| rr-cloudflared | Public radio URL (quick or named tunnel) |
| rr-youtube | Idle until `RR_YOUTUBE_RTMP_URL` set |
| vsftpd | FileZilla Client drag-drop (user `rrftp`) |

## Chronological drop-ins (no new processes)
```
/home/ubuntu/rootrecord/chronological/
  always-on/*.py       # long-running loops
  since-last-fire/*.py # interval pollers
  on-time/*.py         # clocked near :00/:15/:30/:45 (+ pack slots)
  assets/**            # non-.py auto-packed (also any non-.py under chrono/)
```
After each successful pack send, the whole stack restarts so new drop-ins load.

## Drag-drop files (FileZilla Client)
1. **SFTP (recommended):** host = Elastic IP, port 22, user `ubuntu`, key = `rootrecordkey.pem`
2. **FTP:** host = Elastic IP, port 21, user `rrftp`, password in `/home/ubuntu/rootrecord/etc/ftp.password` on the box  
   (Linux has no FileZilla *Server* package — vsftpd speaks FileZilla Client.)

Browse `/home/ubuntu/rootrecord/` — edit folders and drop Python or assets freely.

## Radio listen
- Public (live): `https://choice-cities-establishment-million.trycloudflare.com/rootrecord.mp3`
- Canonical copy of that URL: `/home/ubuntu/rootrecord/etc/radio-public.url` (also in every datapack under `sysmon/`)
- SSH forward still works: `ssh -L 8000:127.0.0.1:8000 rr-aws` → `http://127.0.0.1:8000/rootrecord.mp3`
- Music beds: `radio/media/` (65 mp3s, not wiped). Re-sync: `bash ~/.ollama/skills/rootrecord-aws/local/sync_music_to_aws.sh`
- Quick-tunnel hostname changes if `rr-cloudflared` restarts — post-pack restarts skip it. For a permanent name, set `RR_CLOUDFLARED_TOKEN` and restart cloudflared once.

## Local clock
- Ingest+prep: `:10/:25/:40/:55` — publish: `:00/:15/:30/:45`
- Soft-parked collectors: nws-hawaii, council-quake, earthquake-hourly, radar-archive, rr-kilauea, hurricane-fetch, hurricane-tracker, rr-noaa
- Soft-park desk *play* (after AWS radio is live): `bash ~/.ollama/skills/rootrecord-aws/local/soft_park_audio.sh`
- **Stays local:** EcoFlow BLE, NPU/AI speak, council LLM, origin/console

## Every zip includes
- All work Current.* (timestamped names in the zip)
- `sysmon/` — process logs + free/df/ps/systemctl snapshot
- Chronological non-.py assets
- Never includes `.py` sources

## YouTube go-live
1. YouTube Studio → Go live → copy RTMP URL+key
2. On AWS secrets: `RR_YOUTUBE_RTMP_URL=rtmp://a.rtmp.youtube.com/live2/YOURKEY`
3. `ssh rr-aws 'sudo systemctl restart rr-youtube'`

## Named Cloudflare tunnel (optional permanent hostname)
Set `RR_CLOUDFLARED_TOKEN=` in secrets, then `sudo systemctl restart rr-cloudflared`.

## Deploy
```bash
bash ~/.ollama/skills/rootrecord-aws/aws/deploy.sh
bash ~/.ollama/skills/rootrecord-aws/local/sync_music_to_aws.sh
```
