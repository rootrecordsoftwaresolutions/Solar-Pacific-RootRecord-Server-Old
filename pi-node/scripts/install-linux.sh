#!/usr/bin/env bash
# Official Linux install: https://minepi.com/pi-blockchain/pi-node/linux/
# Run in a terminal so sudo can ask for a password. Do not pass NODE_SEED here.
set -euo pipefail
export DISPLAY="${DISPLAY:-:0}"

echo "Pi Node Linux install (apt.minepi.com)"
echo "Official floor: 150 GB. Prefer Archives, not this NVMe."
echo

sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://apt.minepi.com/repository.gpg.key | sudo gpg --dearmor -o /etc/apt/keyrings/pinetwork-archive-keyring.gpg
sudo chmod a+r /etc/apt/keyrings/pinetwork-archive-keyring.gpg

echo 'deb [arch=amd64 signed-by=/etc/apt/keyrings/pinetwork-archive-keyring.gpg] https://apt.minepi.com stable main' | sudo tee /etc/apt/sources.list.d/pinetwork.list >/dev/null

sudo apt-get update
sudo apt-get install -y pi-node
pi-node --version

if docker --version 2>&1 | grep -qi podman; then
  echo
  echo "This box emulates Docker with Podman. Official Pi Node wants Docker Engine."
  echo "Installing Ubuntu docker.io removes the podman-docker wrapper."
  read -r -p "Install docker.io now? [y/N] " ans
  if [[ "${ans:-}" =~ ^[Yy]$ ]]; then
    sudo apt-get install -y docker.io
    sudo usermod -aG docker "$USER" || true
    sudo systemctl enable --now docker || true
    echo "If docker still fails, log out and back in so group docker applies."
  fi
fi

dir=""
if command -v zenity >/dev/null 2>&1; then
  dir=$(zenity --file-selection --directory --title="Pi Node — choose data directory" --filename="/mnt/Archives/" || true)
fi
if [ -z "${dir:-}" ]; then
  echo
  echo "No folder picked. Next: pi-node initialize"
  exec pi-node initialize
fi

mkdir -p "$HOME/.ollama/skills/pi-node/state"
printf '%s\n' "$dir" > "$HOME/.ollama/skills/pi-node/state/pi-folder.txt"
echo "Data directory: $dir"
exec pi-node initialize --pi-folder "$dir" --docker-volumes "$dir/docker_volumes"
