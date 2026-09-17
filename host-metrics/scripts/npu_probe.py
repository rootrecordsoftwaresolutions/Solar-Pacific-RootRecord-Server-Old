#!/usr/bin/env python3
"""Kernel vs userspace NPU fact sheet. Does not invent load or watts."""
from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path

ACCEL = Path("/dev/accel/accel0")
AMDXDNA = Path("/sys/bus/pci/drivers/amdxdna")


def _run(cmd: list[str], timeout: int = 8) -> str:
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    except Exception as exc:
        return str(exc)
    return ((out.stdout or "") + (out.stderr or "")).strip()


def _memlock_kb() -> str:
    try:
        import resource

        soft, hard = resource.getrlimit(resource.RLIMIT_MEMLOCK)
        return f"soft={soft} hard={hard}"
    except Exception:
        return _run(["bash", "-lc", "ulimit -l"])


def probe() -> dict:
    pci = _run(["lspci", "-nn", "-d", "1022:17f0"])
    xrt = _run(["xrt-smi", "examine"])
    mmap_fail = "mmap(" in xrt and "failed" in xrt.lower()
    devices_found = (not mmap_fail) and "0 devices found" not in xrt.lower() and "npu" in xrt.lower()
    n_enum = None
    enum_err = None
    try:
        import pyxrt

        n_enum = int(pyxrt.enumerate_devices())
    except Exception as exc:
        enum_err = str(exc)
    row = {
        "accel": str(ACCEL) if ACCEL.exists() else None,
        "amdxdna_sysfs": AMDXDNA.is_dir(),
        "pci_17f0": pci or None,
        "user": os.getenv("USER"),
        "render_group": "render" in _run(["id", "-nG"]),
        "memlock": _memlock_kb(),
        "pyxrt_enumerate": n_enum,
        "pyxrt_error": enum_err,
        "xrt_smi": xrt.splitlines()[-8:] if xrt else [],
        "xrt_sees_npu": devices_found,
        "mmap_eagain": mmap_fail,
        "ollama_vulkan": os.environ.get("OLLAMA_VULKAN"),
        "note": (
            "RyzenAI-npu6 is live when ulimit -l is unlimited. "
            "Stock Ollama GGUF does not use this NPU. "
            "Validate archive: ~/.local/share/xrt/2.21.75/amdxdna/bins/xrt_smi_strx.a"
        ),
    }
    return row


def main() -> int:
    print(json.dumps(probe(), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
