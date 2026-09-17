#!/usr/bin/env bash
# Provision Ubuntu Server for RootMC + RootRecord ecosystems.
# Installs toolchains on MAIN SSD; source trees stay on SATA/E (Work Stations).
#
# Usage:
#   sudo bash ubuntu-provision-ecosystem.sh
#
# Env overrides:
#   WORKSTATIONS=/mnt/e/.1\ Work\ Stations
#   SKIP_OLLAMA=1 SKIP_ANDROID=1 SKIP_NPM_PROJECTS=1 INSTALL_DOCKER=1
#   OLLAMA_MODEL=ava-ivy   # after llama baseline create; or qwen2.5-coder:7b
#   OLLAMA_BASE_MODEL=llama3.1:8b
#   AVA_LLAMA_BASELINE="/mnt/e/Ava Ivy/llama-baseline"
#   PROVISION_USER=ubuntu   # home for nvm/npm globals if not run as that user
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
WORKSTATIONS="${WORKSTATIONS:-/mnt/e/.1 Work Stations}"
OLLAMA_MODEL="${OLLAMA_MODEL:-ava-ivy}"
OLLAMA_BASE_MODEL="${OLLAMA_BASE_MODEL:-llama3.1:8b}"
AVA_LLAMA_BASELINE="${AVA_LLAMA_BASELINE:-/mnt/e/Ava Ivy/llama-baseline}"
PROVISION_USER="${PROVISION_USER:-${SUDO_USER:-ubuntu}}"
USER_HOME="$(getent passwd "$PROVISION_USER" | cut -d: -f6)"
if [[ -z "$USER_HOME" || ! -d "$USER_HOME" ]]; then
  USER_HOME="/home/$PROVISION_USER"
fi

log() { echo ""; echo "==> $*"; }

# ---------------------------------------------------------------------------
# 1) Base apt
# ---------------------------------------------------------------------------
log "apt update + base packages"
apt-get update -y
apt-get install -y \
  ca-certificates curl wget gnupg lsb-release software-properties-common \
  build-essential git jq unzip zip tar rsync \
  openssh-server \
  python3 python3-pip python3-venv \
  ffmpeg \
  pkg-config libssl-dev \
  sqlite3 \
  tmux htop

# ---------------------------------------------------------------------------
# 2) Node.js 22.x LTS (NodeSource)
# ---------------------------------------------------------------------------
log "Node.js 22.x"
if ! command -v node >/dev/null 2>&1 || [[ "$(node -v 2>/dev/null | sed 's/v//;s/\..*//')" -lt 20 ]]; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs
fi
node -v
npm -v

log "Global npm tools (pnpm, wrangler) as $PROVISION_USER"
sudo -u "$PROVISION_USER" -H bash -lc 'npm install -g pnpm wrangler'

# ---------------------------------------------------------------------------
# 3) Temurin JDK 17 (Gradle) + JDK 25 (Paper 26 plugins)
# ---------------------------------------------------------------------------
log "Temurin JDK 17 + 25"
mkdir -p /etc/apt/keyrings
if [[ ! -f /etc/apt/keyrings/adoptium.asc ]]; then
  wget -qO /etc/apt/keyrings/adoptium.asc https://packages.adoptium.net/artifactory/api/gpg/key/public
fi
echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "$VERSION_CODENAME") main" \
  >/etc/apt/sources.list.d/adoptium.list
apt-get update -y
apt-get install -y temurin-17-jdk temurin-25-jdk || {
  log "Adoptium apt failed — trying snap/temurin fallbacks"
  apt-get install -y openjdk-17-jdk || true
}

JAVA17="$(dirname "$(dirname "$(readlink -f "$(command -v java || true)")")")"
# Prefer explicit Temurin paths
for c in /usr/lib/jvm/temurin-17-jdk-amd64 /usr/lib/jvm/temurin-17-jdk*; do
  [[ -d "$c" ]] && JAVA17="$c" && break
done
JAVA25=""
for c in /usr/lib/jvm/temurin-25-jdk-amd64 /usr/lib/jvm/temurin-25-jdk*; do
  [[ -d "$c" ]] && JAVA25="$c" && break
done

update-alternatives --install /usr/bin/java java "$JAVA17/bin/java" 1700 || true
[[ -n "$JAVA25" ]] && update-alternatives --install /usr/bin/javac javac "$JAVA25/bin/javac" 2500 || true

log "JAVA17=$JAVA17"
log "JAVA25=${JAVA25:-not installed — install Temurin 25 manually if plugins fail}"

# ---------------------------------------------------------------------------
# 4) GitHub CLI
# ---------------------------------------------------------------------------
log "GitHub CLI"
if ! command -v gh >/dev/null 2>&1; then
  curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
    | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg 2>/dev/null
  chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
    >/etc/apt/sources.list.d/github-cli.list
  apt-get update -y
  apt-get install -y gh
fi

# ---------------------------------------------------------------------------
# 5) cloudflared
# ---------------------------------------------------------------------------
log "cloudflared"
if ! command -v cloudflared >/dev/null 2>&1; then
  curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg \
    | tee /usr/share/keyrings/cloudflare-main.gpg >/dev/null
  echo "deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared $(. /etc/os-release && echo "$VERSION_CODENAME") main" \
    >/etc/apt/sources.list.d/cloudflared.list
  apt-get update -y
  apt-get install -y cloudflared || {
    log "cloudflared apt failed — downloading binary"
    curl -fsSL -o /tmp/cloudflared.deb \
      https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
    dpkg -i /tmp/cloudflared.deb || apt-get install -f -y
  }
fi

# ---------------------------------------------------------------------------
# 6) Ollama + coding model (local llama-class brain)
# ---------------------------------------------------------------------------
if [[ "${SKIP_OLLAMA:-0}" != "1" ]]; then
  log "Ollama"
  if ! command -v ollama >/dev/null 2>&1; then
    curl -fsSL https://ollama.com/install.sh | sh
  fi
  systemctl enable --now ollama 2>/dev/null || true
  if [[ -f "$AVA_LLAMA_BASELINE/scripts/create-ava-ivy.sh" ]]; then
    log "Create Ava Llama baseline ($OLLAMA_MODEL) from $AVA_LLAMA_BASELINE"
    chmod +x "$AVA_LLAMA_BASELINE/scripts/create-ava-ivy.sh" || true
    sudo -u "$PROVISION_USER" -H bash -lc "
      export OLLAMA_MODELS=\"\${OLLAMA_MODELS:-$AVA_LLAMA_BASELINE/ollama-models}\"
      bash \"$AVA_LLAMA_BASELINE/scripts/create-ava-ivy.sh\"
    " || {
      log "WARN: ava-ivy create failed — pulling $OLLAMA_BASE_MODEL then retry create"
      sudo -u "$PROVISION_USER" -H bash -lc "ollama pull '$OLLAMA_BASE_MODEL'" || true
      sudo -u "$PROVISION_USER" -H bash -lc "cd '$AVA_LLAMA_BASELINE' && ollama create '$OLLAMA_MODEL' -f Modelfile" || \
        log "WARN: ollama create failed — set AVA_OLLAMA_MODEL later"
    }
  else
    log "Pull model $OLLAMA_MODEL (CPU; may take a while) — no llama-baseline pack at $AVA_LLAMA_BASELINE"
    sudo -u "$PROVISION_USER" -H bash -lc "ollama pull '$OLLAMA_MODEL'" || \
      ollama pull "$OLLAMA_MODEL" || log "WARN: ollama pull failed — retry later"
  fi
  # Optional second model (coding organizer)
  if [[ "${OLLAMA_EXTRA_MODEL:-}" != "" ]]; then
    ollama pull "$OLLAMA_EXTRA_MODEL" || true
  fi
else
  log "SKIP_OLLAMA=1"
fi

# ---------------------------------------------------------------------------
# 7) Android cmdline tools (optional)
# ---------------------------------------------------------------------------
if [[ "${SKIP_ANDROID:-0}" != "1" ]]; then
  log "Android SDK cmdline tools → /opt/android-sdk"
  ANDROID_HOME=/opt/android-sdk
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    TMP=/tmp/android-cmdline.zip
    curl -fsSL -o "$TMP" \
      https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    rm -rf /tmp/cmdline-tools
    unzip -q -o "$TMP" -d /tmp
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
    # zip contains cmdline-tools/*
    if [[ -d /tmp/cmdline-tools ]]; then
      mv /tmp/cmdline-tools/* "$ANDROID_HOME/cmdline-tools/latest/" || \
        mv /tmp/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
    fi
  fi
  chown -R "$PROVISION_USER:$PROVISION_USER" "$ANDROID_HOME"
  PROFILE_SNIP="$USER_HOME/.config/rootmc-android-env.sh"
  mkdir -p "$(dirname "$PROFILE_SNIP")"
  cat >"$PROFILE_SNIP" <<EOF
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=\$ANDROID_HOME
export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools
EOF
  chown "$PROVISION_USER:$PROVISION_USER" "$PROFILE_SNIP"
  grep -q rootmc-android-env "$USER_HOME/.bashrc" 2>/dev/null || \
    echo '[ -f "$HOME/.config/rootmc-android-env.sh" ] && . "$HOME/.config/rootmc-android-env.sh"' >>"$USER_HOME/.bashrc"
  sudo -u "$PROVISION_USER" -H bash -lc '
    . "$HOME/.config/rootmc-android-env.sh"
    yes | sdkmanager --licenses >/dev/null || true
    sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" || true
  ' || log "WARN: sdkmanager partial — fix ANDROID_HOME and rerun"
else
  log "SKIP_ANDROID=1"
fi

# ---------------------------------------------------------------------------
# 8) Docker (optional)
# ---------------------------------------------------------------------------
if [[ "${INSTALL_DOCKER:-0}" == "1" ]]; then
  log "Docker"
  apt-get install -y docker.io docker-compose-v2 || true
  usermod -aG docker "$PROVISION_USER" || true
fi

# ---------------------------------------------------------------------------
# 9) Linux local.properties example for plugins
# ---------------------------------------------------------------------------
PLUGIN_DIR="$WORKSTATIONS/RootMC/Plugin Building/Minecraft"
if [[ -d "$PLUGIN_DIR" ]]; then
  log "Write local.properties.linux example"
  cat >"$PLUGIN_DIR/local.properties.linux" <<EOF
# Copy to local.properties on this host (do not commit secrets).
java.version=25
org.gradle.java.home=${JAVA17}
# Temurin 25 for compile — Gradle toolchain uses installations.paths
# org.gradle.java.installations.paths=${JAVA25:-/usr/lib/jvm/temurin-25-jdk-amd64}
rootmc.generation=gen1
rootmc.live.plugins.dir=$WORKSTATIONS/RootMC/Server Handoffs/2. RootMC - Towny/plugins
rootmc.workspace.handoff.plugins.dir=$WORKSTATIONS/RootMC/Server Handoffs/2. RootMC - Towny/plugins
EOF
  chown "$PROVISION_USER:$PROVISION_USER" "$PLUGIN_DIR/local.properties.linux" || true
fi

# ---------------------------------------------------------------------------
# 10) npm ci in key projects (SSD node_modules next to source on E — OK)
# ---------------------------------------------------------------------------
if [[ "${SKIP_NPM_PROJECTS:-0}" != "1" && -d "$WORKSTATIONS/RootMC/Web Files" ]]; then
  log "npm ci / npm install in Web Files projects"
  mapfile -t PROJECTS < <(find "$WORKSTATIONS/RootMC/Web Files" -maxdepth 2 -name package.json \
    ! -path '*/node_modules/*' ! -path '*/halted-development/*' 2>/dev/null | sort)
  for pj in "${PROJECTS[@]}"; do
    dir="$(dirname "$pj")"
    base="$(basename "$dir")"
    log "npm in $base"
    sudo -u "$PROVISION_USER" -H bash -lc "
      cd \"$dir\"
      if [[ -f package-lock.json ]]; then npm ci --no-fund --no-audit || npm install --no-fund --no-audit
      else npm install --no-fund --no-audit
      fi
    " || log "WARN: npm failed in $base"
  done
else
  log "SKIP_NPM_PROJECTS=1 or Work Stations missing at $WORKSTATIONS"
fi

# ---------------------------------------------------------------------------
# 11) Marker + summary
# ---------------------------------------------------------------------------
MARK="/var/lib/rootmc-ubuntu-provisioned.txt"
{
  echo "provisioned=$(date -Iseconds)"
  echo "user=$PROVISION_USER"
  echo "workstations=$WORKSTATIONS"
  echo "node=$(node -v 2>/dev/null || echo none)"
  echo "java17=$JAVA17"
  echo "java25=${JAVA25:-none}"
  echo "ollama=$(command -v ollama || echo none)"
  echo "ollama_model=$OLLAMA_MODEL"
  echo "wrangler=$(sudo -u "$PROVISION_USER" -H bash -lc 'wrangler --version' 2>/dev/null || echo none)"
} | tee "$MARK"

log "DONE — see Server Handoffs/Ava Ivy/notes/UBUNTU-DEPENDENCIES.md"
log "Next: copy .env, cloudflared login if needed, systemctl enable ava-ivy"
echo ""
echo "Smoke:"
echo "  node -v && ollama list && wrangler --version"
echo "  cd \"$WORKSTATIONS/RootMC/Web Files/rootmc-ava\" && npm start"
