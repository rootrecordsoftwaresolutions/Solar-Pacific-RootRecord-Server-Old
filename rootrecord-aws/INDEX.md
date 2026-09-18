# rootrecord-aws

| Path | Role |
|------|------|
| `aws/bin/` | EC2 pollers, packer, drop-ins, radio, sysmon |
| `aws/systemd/` | EC2 unit files |
| `aws/deploy.sh` | Admin deploy to `rr-aws` |
| `local/ingest.py` | Catch-up all missed Telegram packs → archive + live |
| `local/report_prep.py` | Facts prep + optional FastFlowLM briefing |
| `local/publish.py` | Publish only `:00/:15/:30/:45` |
| `local/catchup.sh` | One-shot ingest+prep (+publish on mark) |
| `local/trigger_watch.py` | Reply-now (dedicated bot; not datapack recv) |
| `local/install-local-timers.sh` | User timers |
| `local/sync_music_to_aws.sh` | Music beds → AWS radio/media |
| `local/soft_park.sh` / `soft_park_audio.sh` | OFFLOADED markers |
| `store/` | incoming / archive / live / prep / published |
| `references/OPERATOR.md` | Human ops handbook |
| `references/AI-SERVER.md` | AI agent map of the AWS server |
| FileZilla logins | `~/Documents/rootrecord-aws-filezilla.env` (chmod 600) |

SSH: `rr-aws` (`3.16.29.76`). EcoFlow never offloaded. Console owns the desk.
