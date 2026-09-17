#!/bin/bash
# Root-only. Stop skill XMRig and release hugepage reservations.
set -u
BIN="/home/rootrecord/.ollama/skills/xmrig/runtime/xmrig"
if [ "$(id -u)" -ne 0 ]; then
  echo "need_root" >&2
  exit 1
fi
pkill -TERM -f "$BIN" 2>/dev/null || true
sleep 2
pkill -KILL -f "$BIN" 2>/dev/null || true
echo 0 >/sys/kernel/mm/hugepages/hugepages-1048576kB/nr_hugepages 2>/dev/null || true
echo 0 >/proc/sys/vm/nr_hugepages 2>/dev/null || true
exit 0
