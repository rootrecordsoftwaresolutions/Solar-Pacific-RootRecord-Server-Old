# rootrecord-aws

| Path | Role |
|------|------|
| `aws/bin/` | EC2 pollers, packer, drop-ins, radio, sysmon |
| `aws/systemd/` | EC2 unit files |
| `aws/deploy.sh` | Admin deploy to `rr-aws` |
| `local/ingest.py` | Telegram pack download (:10/:25/:40/:55) |
| `local/report_prep.py` | Prep after ingest |
| `local/publish.py` | Publish only :00/:15/:30/:45 |
| `local/trigger_watch.py` | Reply-now when console up |
| `local/sync_music_to_aws.sh` | Music beds → AWS radio/media |
| `local/soft_park.sh` | Soft-park collectors (OFFLOADED) |
| `local/soft_park_audio.sh` | Soft-park desk play once AWS radio live |
| `store/` | Local live/prep/published after ingest |
| `references/OPERATOR.md` | Secrets + ops |

SSH host: `rr-aws` (`3.16.29.76`). EcoFlow never offloaded.

Chronological drop-ins on AWS: `/home/ubuntu/rootrecord/chronological/{always-on,since-last-fire,on-time,assets}`.
FileZilla: SFTP as `ubuntu` with PEM, or FTP as `rrftp` (password in `etc/ftp.password` on the box).
Radio public URL: `etc/radio-public.url` on AWS (also inside each datapack `sysmon/`).
