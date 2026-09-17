#!/usr/bin/env bash
# Ubuntu NPU XRT userspace. Kernel amdxdna is already loaded on this OmniBook.
# Does not install AMD Ryzen AI 1.8 (EULA, Ubuntu 24.04 debs, Python 3.12).
set -euo pipefail
sudo apt-get update
sudo apt-get install -y libxrt-npu2 libxrt-utils-npu python3-xrt

# XRT MAP_LOCKEDs a 64 MiB NPU BAR. Default user memlock is 8 MiB → mmap EAGAIN.
# PAM limits.d is not enough: GNOME's user@UID manager keeps 8M until user@.service
# gets LimitMEMLOCK and you start a new session.
sudo tee /etc/security/limits.d/99-amdxdna.conf >/dev/null <<'EOF'
@render   soft   memlock   unlimited
@render   hard   memlock   unlimited
EOF
sudo mkdir -p /etc/systemd/user.conf.d /etc/systemd/system/user@.service.d
sudo tee /etc/systemd/user.conf.d/99-amdxdna.conf >/dev/null <<'EOF'
[Manager]
DefaultLimitMEMLOCK=infinity
EOF
sudo tee /etc/systemd/system/user@.service.d/99-amdxdna.conf >/dev/null <<'EOF'
[Service]
LimitMEMLOCK=infinity
EOF
sudo systemctl daemon-reload

echo "memlock files written. Log out of the desktop session (or reboot), then:"
echo "  ulimit -l"
echo "  xrt-smi examine"
echo "ulimit -l must not be 8192."
python3 "$(dirname "$0")/npu_probe.py"
