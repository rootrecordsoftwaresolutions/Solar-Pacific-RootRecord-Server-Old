#!/bin/bash
# Root-only. Reserve RandomX pages + load msr.
# drop_caches only if compact fails — otherwise the dataset stays on slow pages.
set -u
BIN_HINT="/home/rootrecord/.ollama/skills/xmrig/runtime/xmrig"
if [ "$(id -u)" -ne 0 ]; then
  echo "need_root" >&2
  exit 1
fi

modprobe msr 2>/dev/null || true

HP2=/sys/kernel/mm/hugepages/hugepages-2048kB/nr_hugepages
HP1=/sys/kernel/mm/hugepages/hugepages-1048576kB/nr_hugepages
FREE2=/sys/kernel/mm/hugepages/hugepages-2048kB/free_hugepages
FREE1=/sys/kernel/mm/hugepages/hugepages-1048576kB/free_hugepages

compact() {
  echo 1 >/proc/sys/vm/compact_memory 2>/dev/null || true
}

drop_once() {
  sync
  echo 3 >/proc/sys/vm/drop_caches 2>/dev/null || true
  compact
  sleep 1
}

compact

echo 0 >"$HP1" 2>/dev/null || true
echo 0 >"$HP2" 2>/dev/null || true
echo 0 >/proc/sys/vm/nr_hugepages 2>/dev/null || true

echo 3 >"$HP1" 2>/dev/null || true
oneg="$(cat "$HP1" 2>/dev/null || echo 0)"
if [ "${oneg:-0}" -lt 3 ]; then
  drop_once
  echo 3 >"$HP1" 2>/dev/null || true
  oneg="$(cat "$HP1" 2>/dev/null || echo 0)"
fi

if [ "${oneg:-0}" -ge 3 ]; then
  echo 64 >"$HP2" 2>/dev/null || echo 64 >/proc/sys/vm/nr_hugepages
else
  echo 0 >"$HP1" 2>/dev/null || true
  got=0
  for n in 1280 1168 1024 768; do
    echo "$n" >"$HP2" 2>/dev/null || echo "$n" >/proc/sys/vm/nr_hugepages
    got="$(cat "$HP2" 2>/dev/null || echo 0)"
    if [ "${got:-0}" -ge 1168 ]; then
      break
    fi
    drop_once
  done
fi

echo "msr=$(lsmod | awk '/^msr /{print 1; found=1} END{if(!found) print 0}')"
echo "hp1_nr=$(cat "$HP1" 2>/dev/null || echo 0) hp1_free=$(cat "$FREE1" 2>/dev/null || echo 0)"
echo "hp2_nr=$(cat "$HP2" 2>/dev/null || echo 0) hp2_free=$(cat "$FREE2" 2>/dev/null || echo 0)"
grep -E 'HugePages_|MemAvailable' /proc/meminfo
ls -l /dev/cpu/0/msr 2>/dev/null || true
echo "bin=$BIN_HINT"
exit 0
