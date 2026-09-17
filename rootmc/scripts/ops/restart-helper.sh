#!/usr/bin/env bash
# RootMC restart helper — Paper runs this from spigot.yml restart-script after a graceful stop.
#
# Shockbyte / Pterodactyl: do NOT start a second Java. Wings owns the process; when the
# original PID exits the panel marks the server stopped and a helper-spawned JVM is reaped
# or left unmanaged. Panel → Tasks → Restart (or crash auto-start) is what brings it back.
ROOT="/home/rootrecord/.ollama/skills/origin/workstations/minecraft-plugins"
LOG="$ROOT/restart.log"
mkdir -p "$ROOT" 2>/dev/null || true
{
  echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] restart-helper invoked (pwd=$(pwd) P_SERVER_UUID=${P_SERVER_UUID:-unset} JARFILE=${SERVER_JARFILE:-})"
  cd "$ROOT" || exit 1

  if [ -n "${P_SERVER_UUID:-}" ] || [ -n "${P_SERVER_LOCATION:-}" ]; then
    echo "[root-restart] Pterodactyl/Shockbyte detected — not spawning Java. Panel must Start/Restart."
    exit 0
  fi

  sleep 2

  if [ -x "./start.sh" ]; then
    echo "[root-restart] exec ./start.sh"
    exec ./start.sh
  fi

  JAR=""
  if [ -n "${SERVER_JARFILE:-}" ] && [ -f "$ROOT/$SERVER_JARFILE" ]; then
    JAR="$ROOT/$SERVER_JARFILE"
  elif [ -n "${SERVER_JARFILE:-}" ] && [ -f "$SERVER_JARFILE" ]; then
    JAR="$SERVER_JARFILE"
  fi
  if [ -z "$JAR" ]; then
    for f in "$ROOT"/paper-*.jar "$ROOT"/server.jar "$ROOT"/purpur-*.jar; do
      if [ -f "$f" ]; then
        JAR="$f"
        break
      fi
    done
  fi
  if [ -z "$JAR" ]; then
    echo "[root-restart] ERROR: no paper jar found in $ROOT" >&2
    exit 1
  fi

  MEM="${SERVER_MEMORY:-${INIT_MEMORY:-4096}}"
  case "$MEM" in
    *M|*G) ;;
    *) MEM="${MEM}M" ;;
  esac
  FLAGS="${JAVA_FLAGS:-${JVM_FLAGS:-}}"

  echo "[root-restart] Starting $JAR from $ROOT (mem=$MEM)"
  exec java $FLAGS -Xms"$MEM" -Xmx"$MEM" -jar "$JAR" nogui
} >>"$LOG" 2>&1
