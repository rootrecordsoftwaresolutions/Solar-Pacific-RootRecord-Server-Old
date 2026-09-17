# Desk — git-auto-push

Runtime: Linux timer + Windows git-sync helpers.
Do not invent watts, SOC, or player counts. Do not dump tokens.

| In this desk | Role |
| --- | --- |
| `scripts/auto-push.sh` | systemd user timer entry |
| `scripts/ava-github-push.sh` | wrapper |
| `scripts/ava-github-push.mjs` | multi-repo push |
| `scripts/github-auto-push-toggle.sh` | on/off flag + timer |
| `scripts/auto-push.py` | Windows Task Scheduler |
| `scripts/auto-pull.py` | Windows pull |
| `scripts/auto-pull-server.py` | Fast-forward pull |
| `references/systemd/` | Parked pull units |
