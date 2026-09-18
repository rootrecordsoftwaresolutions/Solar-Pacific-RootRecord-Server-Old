#!/usr/bin/env bash
# Install user systemd timers for RootRecord local clock (HST).
set -euo pipefail
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
SKILL="$HOME/.ollama/skills/rootrecord-aws"
mkdir -p "$UNIT_DIR" "$SKILL/store"/{incoming,live,prep,published,state,triggers} "$SKILL/local/etc"

PY="${RR_PYTHON:-python3}"
# Prefer venv if present
if [[ -x "$HOME/.ollama/skills/rootrecord-aws/.venv/bin/python" ]]; then
  PY="$HOME/.ollama/skills/rootrecord-aws/.venv/bin/python"
fi

cat > "$UNIT_DIR/rr-ingest.service" <<EOF
[Unit]
Description=RootRecord datapack ingest

[Service]
Type=oneshot
Environment=TZ=Pacific/Honolulu
WorkingDirectory=$SKILL
ExecStart=$PY $SKILL/local/ingest.py
ExecStartPost=$PY $SKILL/local/report_prep.py
EOF

cat > "$UNIT_DIR/rr-ingest.timer" <<'EOF'
[Unit]
Description=RootRecord ingest at :10/:25/:40/:55 HST

[Timer]
OnCalendar=*-*-* *:10:00
OnCalendar=*-*-* *:25:00
OnCalendar=*-*-* *:40:00
OnCalendar=*-*-* *:55:00
Persistent=true
Unit=rr-ingest.service

[Install]
WantedBy=timers.target
EOF

cat > "$UNIT_DIR/rr-publish.service" <<EOF
[Unit]
Description=RootRecord publish on the mark

[Service]
Type=oneshot
Environment=TZ=Pacific/Honolulu
WorkingDirectory=$SKILL
ExecStart=$PY $SKILL/local/publish.py
EOF

cat > "$UNIT_DIR/rr-publish.timer" <<'EOF'
[Unit]
Description=RootRecord publish at :00/:15/:30/:45 HST

[Timer]
OnCalendar=*-*-* *:00:00
OnCalendar=*-*-* *:15:00
OnCalendar=*-*-* *:30:00
OnCalendar=*-*-* *:45:00
Persistent=true
Unit=rr-publish.service

[Install]
WantedBy=timers.target
EOF

cat > "$UNIT_DIR/rr-trigger-watch.service" <<EOF
[Unit]
Description=RootRecord reply-now trigger watch (console desk only)
ConditionPathExists=%h/.ollama/skills/state/store/ava-console-up

[Service]
Type=simple
Environment=TZ=Pacific/Honolulu
WorkingDirectory=$SKILL
ExecStart=$PY $SKILL/local/trigger_watch.py
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
EOF

# Catch-up on login / after downtime (Persistent timers also fire missed slots)
cat > "$UNIT_DIR/rr-catchup.service" <<EOF
[Unit]
Description=RootRecord offline pack catch-up

[Service]
Type=oneshot
Environment=TZ=Pacific/Honolulu
WorkingDirectory=$SKILL
ExecStart=/bin/bash $SKILL/local/catchup.sh
EOF

systemctl --user daemon-reload
systemctl --user enable --now rr-ingest.timer rr-publish.timer
# trigger-watch only stays up while console flag exists; enable unit for when console is up
systemctl --user enable rr-trigger-watch.service
systemctl --user start rr-trigger-watch.service 2>/dev/null || true
systemctl --user list-timers --all | grep -E 'rr-(ingest|publish)' || true
echo "Local RootRecord timers installed. Fill $SKILL/local/etc/secrets.env"
