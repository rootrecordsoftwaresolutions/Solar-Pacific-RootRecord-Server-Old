pgrep -af "node server.js"    # should show only the globe process
command -v node    # expect /usr/bin/node; if different, change ExecStart below
sudo tee /etc/systemd/system/network-globe.service > /dev/null <<'EOF'
[Unit]
Description=RootRecord Network Globe
After=network-online.target
Wants=network-online.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/network-globe/network-globe
Environment=AWS_REGION=us-east-2
ExecStart=/usr/bin/node server.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

kill 57430
sudo systemctl daemon-reload
sudo systemctl enable --now network-globe.service
sleep 2
systemctl is-active network-globe.service
curl -s http://127.0.0.1:8090/healthz
sudo tee -a /etc/systemd/system/rr-cloudflared-globe.service > /dev/null <<'EOF'

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable rr-cloudflared-globe.service
systemctl is-enabled rr-cloudflared-globe.service network-globe.service
echo "--- Install section count (expect 1) ---"
grep -c '^\[Install\]' /etc/systemd/system/rr-cloudflared-globe.service
echo "--- service states ---"
systemctl is-enabled rr-cloudflared-globe.service network-globe.service
systemctl is-active rr-cloudflared-globe.service network-globe.service
echo "--- public check ---"
curl -s -m 10 https://www.rootrecord.cloud/healthz; echo
echo "--- attached IAM role (empty/404 = none) ---"
TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/iam/security-credentials/; echo
echo "--- how the app uses AWS ---"
cd /home/ubuntu/network-globe/network-globe
grep -n -i -E "aws|ec2|describe|sigv4|credential|amazonaws" server.js | head -50
echo "--- aws cli installed? ---"
command -v aws && aws --version
echo "--- full service list the app queries (lines 405-435) ---"
sed -n '405,435p' /home/ubuntu/network-globe/network-globe/server.js
echo "--- public check with error detail ---"
curl -sS -m 10 -o /dev/null -w "http=%{http_code}\n" https://www.rootrecord.cloud/healthz
echo "--- is the tunnel connected? ---"
journalctl -u rr-cloudflared-globe -n 8 --no-pager
echo "--- install AWS CLI v2 ---"
sudo apt-get install -y unzip >/dev/null 2>&1
curl -sS "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip   && unzip -q -o /tmp/awscliv2.zip -d /tmp   && sudo /tmp/aws/install --update
