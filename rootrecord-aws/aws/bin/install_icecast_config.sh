#!/usr/bin/env bash
# Install ffmpeg/icecast and write icecast.xml from radio.source.password
set -euo pipefail
ROOT="${RR_ROOT:-/home/ubuntu/rootrecord}"
ETC="$ROOT/etc"
PASS_FILE="$ETC/radio.source.password"
ADMIN_FILE="$ETC/radio.admin.password"
mkdir -p "$ETC" "$ROOT/logs" "$ROOT/radio/media" "$ROOT/work/audio"

if [[ ! -f "$PASS_FILE" ]]; then openssl rand -hex 12 > "$PASS_FILE"; chmod 600 "$PASS_FILE"; fi
if [[ ! -f "$ADMIN_FILE" ]]; then openssl rand -hex 12 > "$ADMIN_FILE"; chmod 600 "$ADMIN_FILE"; fi
SRC_PASS="$(tr -d '\n' < "$PASS_FILE")"
ADM_PASS="$(tr -d '\n' < "$ADMIN_FILE")"

# Bind localhost only — public listen via SSH tunnel or future CF tunnel (no open 8000 yet)
cat > "$ETC/icecast.xml" <<EOF
<icecast>
  <location>RootRecord</location>
  <admin>ops@rootrecord.cloud</admin>
  <limits>
    <clients>32</clients>
    <sources>4</sources>
    <queue-size>524288</queue-size>
    <source-timeout>30</source-timeout>
  </limits>
  <authentication>
    <source-password>${SRC_PASS}</source-password>
    <relay-password>${SRC_PASS}</relay-password>
    <admin-user>admin</admin-user>
    <admin-password>${ADM_PASS}</admin-password>
  </authentication>
  <hostname>localhost</hostname>
  <listen-socket>
    <port>8000</port>
    <bind-address>127.0.0.1</bind-address>
  </listen-socket>
  <http-headers>
    <header name="Access-Control-Allow-Origin" value="*" />
  </http-headers>
  <paths>
    <basedir>/usr/share/icecast2</basedir>
    <logdir>${ROOT}/logs</logdir>
    <webroot>/usr/share/icecast2/web</webroot>
    <adminroot>/usr/share/icecast2/admin</adminroot>
    <alias source="/" destination="/status.xsl"/>
  </paths>
  <logging>
    <accesslog>icecast-access.log</accesslog>
    <errorlog>icecast-error.log</errorlog>
    <loglevel>3</loglevel>
  </logging>
  <security>
    <chroot>0</chroot>
  </security>
</icecast>
EOF
chmod 600 "$ETC/icecast.xml"
echo "icecast.xml written (localhost:8000)"
