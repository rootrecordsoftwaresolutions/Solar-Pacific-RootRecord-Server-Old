---
name: host-metrics
description: >-
  Host metrics (no invented watts).
---

# host-metrics

This folder **is** the runtime.

September series reset: `scripts/reset_series.py` (optional `--dry-run`). Moves EcoFlow/host/uptime jsonl aside. Next live sample starts empty.

On this OmniBook, `npu_present` is `/dev/accel/accel0` or `amdxdna`. After reboot,
`ulimit -l` is unlimited and `xrt-smi examine` lists `RyzenAI-npu6` at
`0000:04:00.1`. `npu_pct` is DRM fdinfo busy time on `amdxdna` (idle is 0 when
the device is present and nothing holds `/dev/accel`). FastFlowLM on the NPU
moves that number; stock Ollama GGUF does not. iGPU load is amdgpu
`gpu_busy_percent`. Carly hourly bandwidth uses `host-net.jsonl` byte
counters on the default route (1 hour / 24 hours). Security counts failed
sign-ins in `auth.log`, firewall start-on-boot from `ufw.conf`, and live
listener/connection counts. No invented traffic. No exploit steps.

Validate binaries: `~/.local/share/xrt/2.21.75/amdxdna/bins/xrt_smi_strx.a`
from Xilinx VTD 2.21.75. Probe: `scripts/npu_probe.py`.
Telegram council, origin `:8787` rewrite/core-chat, and local reports already
call `ollama.chat_sync` on FastFlowLM (`ava-flm.service`, console flag required,
default pmode, `:52625`). Launch waits for the NPU before origin. Idle-stop
unmaps it. Chat does not fall back to GGUF. `ollama run` coder/vision still 840M.
XRT source is `https://github.com/Xilinx/XRT`. NPU plugin:
`https://github.com/amd/xdna-driver`. Never `git clone https://github.com`.

Topic index: `boot-idle-origin`.
