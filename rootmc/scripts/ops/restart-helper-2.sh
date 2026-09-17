#!/usr/bin/env bash
# RootMC restart helper — Paper runs this from spigot.yml restart-script after a graceful stop.
ROOT="/home/rootrecord/.ollama/skills/origin/workstations/minecraft-plugins"
LOG="$ROOT/restart.log"
mkdir -p "$ROOT" 2>/dev/null || true
{
  echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] restart-helper invoked (pwd=$(pwd))"
  cd "$ROOT" || exit 1
  sleep 2

  if [ -x "./start.sh" ]; then
    echo "[root-restart] exec ./start.sh"
    exec ./start.sh
  fi

  JAR=""
  for f in "$ROOT"/paper-*.jar; do
    if [ -f "$f" ]; then
      JAR="$f"
      break
    fi
  done
  if [ -z "$JAR" ] && [ -f "$ROOT/server.jar" ]; then
    JAR="$ROOT/server.jar"
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
