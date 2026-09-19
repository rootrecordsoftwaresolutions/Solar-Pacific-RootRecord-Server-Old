#!/usr/bin/env bash
# Install vsftpd for FileZilla Client drag-drop + passwordless sudo restarts.
set -euo pipefail
ROOT=/home/ubuntu/rootrecord
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq vsftpd

# Dedicated FTP user chrooted to rootrecord tree (FileZilla Client → SFTP also works on :22)
if ! id -u rrftp >/dev/null 2>&1; then
  sudo useradd -m -d /home/rrftp -s /usr/sbin/nologin rrftp || true
fi
# Password from file (operator can change)
PASS_FILE=/home/ubuntu/rootrecord/etc/ftp.password
if [[ ! -f "$PASS_FILE" ]]; then
  openssl rand -base64 18 | tr -d '/+=' | head -c 16 > "$PASS_FILE"
  chmod 600 "$PASS_FILE"
fi
PASS="$(tr -d '\n' < "$PASS_FILE")"
echo "rrftp:$PASS" | sudo chpasswd

# Bind mount rootrecord into rrftp home for chroot
sudo mkdir -p /home/rrftp/rootrecord
if ! mountpoint -q /home/rrftp/rootrecord; then
  sudo mount --bind "$ROOT" /home/rrftp/rootrecord
fi
# Persist bind
if ! grep -q 'rootrecord /home/rrftp/rootrecord' /etc/fstab 2>/dev/null; then
  echo "$ROOT /home/rrftp/rootrecord none bind 0 0" | sudo tee -a /etc/fstab >/dev/null
fi
sudo chown -R ubuntu:ubuntu "$ROOT"
sudo chmod 755 /home/rrftp

sudo tee /etc/vsftpd.conf >/dev/null <<'EOF'
listen=YES
listen_ipv6=NO
anonymous_enable=NO
local_enable=YES
write_enable=YES
local_umask=022
dirmessage_enable=YES
use_localtime=YES
xferlog_enable=YES
connect_from_port_20=YES
chroot_local_user=YES
allow_writeable_chroot=YES
pasv_enable=YES
pasv_min_port=40000
pasv_max_port=40100
userlist_enable=YES
userlist_file=/etc/vsftpd.userlist
userlist_deny=NO
secure_chroot_dir=/var/run/vsftpd/empty
pam_service_name=vsftpd
rsa_cert_file=/etc/ssl/certs/ssl-cert-snakeoil.pem
rsa_private_key_file=/etc/ssl/private/ssl-cert-snakeoil.key
ssl_enable=NO
EOF
echo rrftp | sudo tee /etc/vsftpd.userlist >/dev/null

# Open FTP + passive in ufw (restrict later to home IP if desired)
sudo ufw allow 21/tcp || true
sudo ufw allow 40000:40100/tcp || true

sudo systemctl enable --now vsftpd

# Passwordless restart for stack (post-pack absorb)
sudo tee /etc/sudoers.d/rr-restart >/dev/null <<'EOF'
ubuntu ALL=(root) NOPASSWD: /bin/bash /home/ubuntu/rootrecord/bin/restart_all.sh
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-weather.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-earthquake.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-radar.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-hurricane.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-noaa.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-chat.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-audio-recv.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-dropins.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-icecast.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-radio.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-youtube.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-cloudflared.service
ubuntu ALL=(root) NOPASSWD: /bin/systemctl restart rr-packer.service
EOF
sudo chmod 440 /etc/sudoers.d/rr-restart
sudo visudo -cf /etc/sudoers.d/rr-restart

echo "FTP user=rrftp password in $PASS_FILE"
echo "FileZilla Client: host=<elastic-ip> user=rrftp protocol=FTP"
echo "Or SFTP as ubuntu with your PEM (recommended)"
