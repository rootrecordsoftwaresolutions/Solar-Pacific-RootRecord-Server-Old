#!/bin/bash
# Root-only. Passwordless sudo for the three xmrig helper scripts.
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then
  echo "need_root" >&2
  exit 1
fi
DEST=/etc/sudoers.d/ava-xmrig
cat >"$DEST" <<'EOF'
# Ava-Core xmrig skill — hugepages + MSR. Narrow helpers only.
rootrecord ALL=(root) NOPASSWD: /home/rootrecord/.ollama/skills/xmrig/scripts/xmrig_host_prep.sh
rootrecord ALL=(root) NOPASSWD: /home/rootrecord/.ollama/skills/xmrig/scripts/xmrig_run.sh
rootrecord ALL=(root) NOPASSWD: /home/rootrecord/.ollama/skills/xmrig/scripts/xmrig_kill.sh
EOF
chmod 440 "$DEST"
visudo -cf "$DEST"
echo "installed $DEST"
/home/rootrecord/.ollama/skills/xmrig/scripts/xmrig_host_prep.sh
