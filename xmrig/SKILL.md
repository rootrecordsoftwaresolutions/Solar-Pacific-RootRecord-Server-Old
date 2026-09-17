---
name: xmrig
description: >-
  CPU RandomX miner (XMRig) on this OmniBook. Start/stop/status/smoke via
  Python. Four threads. Do not use ltc-node for mining. Do not invent hashrate.
---

# xmrig

This folder **is** the miner. Execute the Python control script. Do not dump the pool user. Do not invent H/s.

Runtime binary: `runtime/xmrig` (6.26.0). Config: `runtime/config.json`.
RandomX **fast**, Ryzen asm, huge pages, JIT, 4 `rx` threads on CPUs `0,1,2,3`. OpenCL/CUDA stay off (iGPU is for Ollama). NPU is not this miner.

## Commands (run these)

```bash
python3 ~/.ollama/skills/xmrig/scripts/xmrig_ctl.py status
python3 ~/.ollama/skills/xmrig/scripts/xmrig_ctl.py start
python3 ~/.ollama/skills/xmrig/scripts/xmrig_ctl.py stop
python3 ~/.ollama/skills/xmrig/scripts/xmrig_ctl.py smoke
```

MSR and 1GB/2MB pages need **root**. Unprivileged start is the slow path you just saw (`FAILED TO APPLY MSR MOD`, `huge pages 0%`).

One-time:

```bash
pkexec ~/.ollama/skills/xmrig/scripts/install_privileges.sh
python3 ~/.ollama/skills/xmrig/scripts/xmrig_ctl.py stop
python3 ~/.ollama/skills/xmrig/scripts/xmrig_ctl.py start
```

## Pipeline

- Ava Ops flags: `POST /api/ops/features` with `"xmrig": true|false` (default off).
- Same process: `POST /api/ops/xmrig` `{"action":"start"|"stop"|"status"|"smoke"}`.
- Idle desk / console close kills it (`idle-stop`).

Local HTTP (restricted): `http://127.0.0.1:18067/1/summary`.

Litecoin **node** files stay on `/mnt/Archives/Litecoin`. This skill is not that disk.

Topic: `ltc-node` for chain status only.
