#!/usr/bin/env bash
# Wire the two new datapack bots after you create them in BotFather.
# Usage:
#   RR_SEND_TOKEN='...' RR_RECV_TOKEN='...' bash wire_bots.sh
# Optional:
#   RR_CHAT_ID='-5587564599'  (default)
set -euo pipefail
CHAT="${RR_CHAT_ID:--5587564599}"
SEND="${RR_SEND_TOKEN:-}"
RECV="${RR_RECV_TOKEN:-}"
if [[ -z "$SEND" || -z "$RECV" ]]; then
  echo "Set RR_SEND_TOKEN and RR_RECV_TOKEN (BotFather tokens for the two new bots)."
  echo "Add both bots as admins on the relay channel, then re-run."
  exit 1
fi
if [[ "$SEND" == "$RECV" ]]; then
  echo "SEND and RECV must be different bots (receiver cannot see its own posts)."
  exit 1
fi

# Resolve chat id: try given, then -100 prefix form
resolve_chat() {
  local token="$1" cand="$2"
  python3 - "$token" "$cand" <<'PY'
import json, sys, urllib.request
token, cand = sys.argv[1], sys.argv[2]
candidates = [cand]
if cand.startswith("-") and not cand.startswith("-100"):
    candidates.append("-100" + cand.lstrip("-"))
for c in candidates:
    url = f"https://api.telegram.org/bot{token}/getChat?chat_id={c}"
    try:
        with urllib.request.urlopen(url, timeout=20) as r:
            data = json.loads(r.read().decode())
        if data.get("ok"):
            print(c)
            sys.exit(0)
    except Exception:
        pass
sys.exit(1)
PY
}

CHAT_RESOLVED="$(resolve_chat "$RECV" "$CHAT" || resolve_chat "$SEND" "$CHAT" || true)"
if [[ -z "${CHAT_RESOLVED}" ]]; then
  echo "Could not resolve chat id $CHAT — add both bots as channel admins, then retry."
  echo "Using $CHAT anyway; fix if send fails."
  CHAT_RESOLVED="$CHAT"
fi
echo "using_chat_id=$CHAT_RESOLVED"

# AWS secrets (send bot)
ssh -o BatchMode=yes rr-aws "python3 - <<'PY'
from pathlib import Path
p = Path('/home/ubuntu/rootrecord/etc/secrets.env')
send = '''${SEND}'''
chat = '''${CHAT_RESOLVED}'''
text = f'''# RootRecord AWS secrets
RR_DATAPACK_SEND_BOT_TOKEN={send}
RR_DATAPACK_CHAT_ID={chat}
RR_DATAPACK_BOT_TOKEN={send}
RR_TELEGRAM_BOT_TOKEN=
RR_WATCH_CHAT_IDS=
RR_CONTROL_CHAT_ID=
'''
p.write_text(text, encoding='utf-8')
p.chmod(0o600)
print('aws_secrets_written')
PY"
ssh -o BatchMode=yes rr-aws 'sudo systemctl restart rr-packer rr-chat'

# Local secrets (recv bot)
LOCAL="$HOME/.ollama/skills/rootrecord-aws/local/etc/secrets.env"
cat > "$LOCAL" <<EOF
# RootRecord local secrets
RR_DATAPACK_RECV_BOT_TOKEN=${RECV}
RR_DATAPACK_CHAT_ID=${CHAT_RESOLVED}
RR_DATAPACK_BOT_TOKEN=${RECV}
RR_CONTROL_CHAT_ID=
RR_ALERT_CHAT_ID=
RR_PUBLISH_CHAT_ID=
EOF
chmod 600 "$LOCAL"
systemctl --user restart rr-trigger-watch.service 2>/dev/null || true

# Smoke: one pack send from AWS
ssh -o BatchMode=yes rr-aws '/home/ubuntu/rootrecord/venv/bin/python /home/ubuntu/rootrecord/bin/packer.py --once' && echo PACK_SEND_OK

# Smoke: local ingest
"$HOME/.ollama/skills/rootrecord-aws/.venv/bin/python" \
  "$HOME/.ollama/skills/rootrecord-aws/local/ingest.py" && echo INGEST_OK

echo "Done. Relay channel $CHAT_RESOLVED wired."
