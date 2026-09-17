"""Live host metrics for AVA-CORE on Windows (and Linux when sensors exist).

OmniBook: Ryzen AI 5 430 + Radeon 840M. Stock Ollama GGUF uses the 840M, not
XDNA. Never invent watts.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import time
from datetime import datetime
from pathlib import Path
from typing import Any

if os.name == "nt":
    import winreg
else:
    winreg = None

import psutil

CREATE_NO_WINDOW = 0x08000000

_LINUX_ACCEL = Path("/dev/accel/accel0")
_LINUX_AMDXDNA = Path("/sys/bus/pci/drivers/amdxdna")
_LINUX_DRM = Path("/sys/class/drm")
_LINUX_PROC = Path("/proc")
_FDINFO_DRIVER = re.compile(r"^drm-driver:\s+(\S+)", re.M)
_FDINFO_CLIENT = re.compile(r"^drm-client-id:\s+(\S+)", re.M)
_FDINFO_ENGINE = re.compile(r"^drm-engine-(\S+):\s+(\d+)", re.M)


def _kelvin_raw_to_c(raw: float) -> float | None:
    """Win32 thermal counters are Kelvin, tenths-Kelvin, or already °C."""
    if 200 <= raw <= 450:
        c = raw - 273.15
    elif 2000 <= raw <= 4500:
        c = raw / 10.0 - 273.15
    elif 10 <= raw <= 120:
        c = raw
    else:
        return None
    if -20 <= c <= 110:
        return round(c, 1)
    return None


def _pdh_double(counter_path: str) -> float | None:
    if os.name != "nt":
        return None
    import ctypes
    from ctypes import wintypes

    pdh = ctypes.windll.pdh
    PDH_FMT_DOUBLE = 0x00000200

    class PDH_FMT_COUNTERVALUE(ctypes.Structure):
        _fields_ = [
            ("CStatus", wintypes.DWORD),
            ("_pad", wintypes.DWORD),
            ("doubleValue", ctypes.c_double),
        ]

    h_query = wintypes.HANDLE()
    if pdh.PdhOpenQueryW(None, None, ctypes.byref(h_query)) != 0:
        return None
    try:
        h_counter = wintypes.HANDLE()
        if pdh.PdhAddEnglishCounterW(h_query, counter_path, None, ctypes.byref(h_counter)) != 0:
            return None
        pdh.PdhCollectQueryData(h_query)
        time.sleep(0.05)
        pdh.PdhCollectQueryData(h_query)
        val = PDH_FMT_COUNTERVALUE()
        if pdh.PdhGetFormattedCounterValue(h_counter, PDH_FMT_DOUBLE, None, ctypes.byref(val)) != 0:
            return None
        if val.CStatus != 0:
            return None
        return float(val.doubleValue)
    except Exception:
        return None
    finally:
        pdh.PdhCloseQuery(h_query)


def host_temp_c() -> tuple[float | None, str | None]:
    """Best-effort thermal zone / CPU temp. Returns (celsius, source)."""
    try:
        temps = getattr(psutil, "sensors_temperatures", lambda: None)() or {}
    except Exception:
        temps = {}
    prefer = ("coretemp", "k10temp", "zenpower", "cpu_thermal", "acpitz", "dell_smm")
    for name in prefer:
        entries = temps.get(name) or []
        for e in entries:
            cur = getattr(e, "current", None)
            if cur is None:
                continue
            try:
                val = float(cur)
            except (TypeError, ValueError):
                continue
            if -20 <= val <= 110:
                return round(val, 1), name
        if entries:
            try:
                val = float(entries[0].current)
                if -20 <= val <= 110:
                    return round(val, 1), name
            except (TypeError, ValueError, IndexError):
                pass
    for name, entries in temps.items():
        for e in entries:
            try:
                val = float(e.current)
            except (TypeError, ValueError, AttributeError):
                continue
            if -20 <= val <= 110:
                return round(val, 1), str(name)
    if os.name == "nt":
        for path in (
            r"\Thermal Zone Information(*)\Temperature",
            r"\Thermal Zone Information(_TZ.THRM)\Temperature",
        ):
            raw = _pdh_double(path)
            if raw is None:
                continue
            c = _kelvin_raw_to_c(raw)
            if c is not None:
                return c, "acpi_thermal_zone"
    return None, None


def host_battery() -> dict[str, Any] | None:
    try:
        batt = psutil.sensors_battery()
    except Exception:
        return None
    if batt is None:
        return None
    pct = getattr(batt, "percent", None)
    if pct is None:
        return None
    try:
        pct_f = round(float(pct), 1)
    except (TypeError, ValueError):
        return None
    plugged = bool(getattr(batt, "power_plugged", False))
    secs = getattr(batt, "secsleft", None)
    out: dict[str, Any] = {"pct": pct_f, "plugged": plugged}
    try:
        secs_i = int(secs)
        if secs_i > 0:
            out["secsleft"] = secs_i
    except (TypeError, ValueError):
        pass
    return out


def _disk_parent(device: str) -> str:
    name = Path(device or "").name
    if not name:
        return device or "disk"
    if name.startswith("nvme") and "p" in name:
        return name.rsplit("p", 1)[0]
    return re.sub(r"\d+$", "", name) or name


def _drive_label(mount: str) -> str:
    if mount in {"/", "C:\\", "C:"}:
        return "system drive"
    tail = mount.rstrip("/").split("/")[-1] or mount
    return f"{tail} drive"


def host_disks() -> list[dict[str, Any]]:
    """One row per physical drive. Skip snap/loop/tmp mounts."""
    skip_fs = {"squashfs", "overlay", "tmpfs", "devtmpfs", "cgroup", "cgroup2", "fuse.portal"}
    skip_mp = ("/snap", "/run", "/sys", "/proc", "/dev", "/boot")
    by_disk: dict[str, dict[str, Any]] = {}
    for part in psutil.disk_partitions(all=False):
        fstype = (part.fstype or "").lower()
        mp = part.mountpoint or ""
        dev = (part.device or "").lower()
        if fstype in skip_fs:
            continue
        if "loop" in dev or "snap" in mp.lower():
            continue
        if any(mp == p or mp.startswith(p + "/") for p in skip_mp):
            continue
        try:
            usage = psutil.disk_usage(mp)
        except OSError:
            continue
        if usage.total < 2 * 1024 ** 3:
            continue
        parent = _disk_parent(part.device)
        row = {
            "disk": parent,
            "mount": mp,
            "label": _drive_label(mp),
            "pct": round(float(usage.percent), 1),
            "used_gb": round(usage.used / (1024 ** 3), 1),
            "total_gb": round(usage.total / (1024 ** 3), 1),
            "total_bytes": int(usage.total),
        }
        prev = by_disk.get(parent)
        if not prev or row["total_bytes"] >= int(prev.get("total_bytes") or 0):
            by_disk[parent] = row
    return list(by_disk.values())


def host_drives_spoken() -> str:
    rows = host_disks()
    if not rows:
        return ""
    bits = []
    for d in rows:
        bits.append(
            f"{d['label']} {d['pct']} percent used, {d['used_gb']} of {d['total_gb']} gigabytes"
        )
    return "Drives: " + ". ".join(bits) + "."


def host_disk_pct(home: Path | None = None) -> float | None:
    target = str(home) if home else os.environ.get("AVA_HOME") or str(Path.home() / "ava")
    try:
        return round(float(psutil.disk_usage(target).percent), 1)
    except OSError:
        try:
            return round(float(psutil.disk_usage("C:\\").percent), 1)
        except OSError:
            return None


def _drm_cards() -> list[Path]:
    if not _LINUX_DRM.is_dir():
        return []
    return sorted(
        p
        for p in _LINUX_DRM.iterdir()
        if p.name.startswith("card") and p.name[4:].isdigit()
    )


def _linux_gpu_name() -> str | None:
    slot = None
    for card in _drm_cards():
        uevent = card / "device" / "uevent"
        if not uevent.is_file():
            continue
        try:
            text = uevent.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if "DRIVER=amdgpu" not in text:
            continue
        for line in text.splitlines():
            if line.startswith("PCI_SLOT_NAME="):
                slot = line.split("=", 1)[1].strip()
                break
        if slot:
            break
    if slot:
        try:
            out = subprocess.run(
                ["lspci", "-s", slot],
                capture_output=True,
                text=True,
                timeout=2,
            )
        except Exception:
            out = None
        line = ((out.stdout if out else "") or "").strip()
        if line:
            return line.split(": ", 1)[-1].strip()
    return "amdgpu" if _linux_gpu_pct() is not None else None


def _linux_gpu_pct() -> float | None:
    for card in _drm_cards():
        path = card / "device" / "gpu_busy_percent"
        if not path.is_file():
            continue
        try:
            return round(min(100.0, max(0.0, float(path.read_text().strip()))), 1)
        except (OSError, ValueError):
            continue
    return None


def _linux_npu_present() -> bool:
    try:
        if _LINUX_ACCEL.exists():
            return True
    except OSError:
        pass
    try:
        if _LINUX_AMDXDNA.is_dir():
            return True
    except OSError:
        pass
    return False


def _linux_npu_fdinfo_busy(proc_root: Path | None = None) -> tuple[dict[str, int], bool]:
    """Per-client amdxdna engine busy nanoseconds from DRM fdinfo."""
    root = proc_root or _LINUX_PROC
    busy: dict[str, int] = {}
    found = False
    try:
        procs = list(root.iterdir())
    except OSError:
        return busy, False
    for proc in procs:
        if not proc.name.isdigit():
            continue
        fdinfo = proc / "fdinfo"
        try:
            files = list(fdinfo.iterdir())
        except OSError:
            continue
        for path in files:
            try:
                txt = path.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            drv = _FDINFO_DRIVER.search(txt)
            if not drv or drv.group(1) != "amdxdna":
                continue
            found = True
            cid = (_FDINFO_CLIENT.search(txt) or drv).group(1)
            for eng, ns in _FDINFO_ENGINE.findall(txt):
                try:
                    val = int(ns)
                except ValueError:
                    continue
                key = f"{cid}:{eng}"
                if val >= busy.get(key, 0):
                    busy[key] = val
    return busy, found


def _linux_npu_pct(*, wait_s: float = 0.15, proc_root: Path | None = None) -> float | None:
    """XDNA busy percent. Idle with no client is 0, not missing."""
    if not _linux_npu_present():
        return None
    first, found = _linux_npu_fdinfo_busy(proc_root)
    if not found:
        return 0.0
    t0 = time.monotonic_ns()
    time.sleep(max(0.05, wait_s))
    second, _found2 = _linux_npu_fdinfo_busy(proc_root)
    dt = time.monotonic_ns() - t0
    if dt <= 0:
        return 0.0
    delta = 0
    for key, later in second.items():
        earlier = first.get(key, 0)
        if later > earlier:
            delta += later - earlier
    return round(min(100.0, max(0.0, 100.0 * delta / dt)), 1)


def gpu_name() -> str | None:
    if os.name != "nt":
        return _linux_gpu_name()
    if winreg is None:
        return None
    class_root = r"SYSTEM\CurrentControlSet\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}"
    try:
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, class_root) as root:
            for i in range(0, 8):
                sub = f"{i:04d}"
                try:
                    with winreg.OpenKey(root, sub) as k:
                        desc, _ = winreg.QueryValueEx(k, "DriverDesc")
                except OSError:
                    continue
                name = str(desc or "").strip()
                if name and "microsoft basic" not in name.lower():
                    return name
    except OSError:
        return None
    return None


def npu_present() -> bool:
    """Device present. Does not mean Ollama is using it."""
    if os.name != "nt":
        return _linux_npu_present()
    if winreg is None:
        return False
    # Compute Accelerator class (AMD XDNA / NPU Compute Accelerator Device)
    try:
        with winreg.OpenKey(
            winreg.HKEY_LOCAL_MACHINE,
            r"SYSTEM\CurrentControlSet\Control\Class\{f01a9d53-3ff6-48d2-9f97-c8a7004be10c}",
        ):
            return True
    except OSError:
        pass
    try:
        with winreg.OpenKey(
            winreg.HKEY_LOCAL_MACHINE,
            r"SYSTEM\CurrentControlSet\Enum\PCI\VEN_1022&DEV_17F0&SUBSYS_8EF8103C&REV_20",
        ):
            return True
    except OSError:
        return False


# GPU Engine PDH is slow the first time. Keep the last good sample for a few seconds.
_gpu_cache: tuple[float, float | None] = (0.0, None)
_npu_cache: tuple[float, float | None] = (0.0, None)
_GPU_CACHE_S = 8.0
_NPU_MISS_RETRY_S = 300.0
_GPU_ENGINE_TYPES = frozenset(
    {"3d", "compute", "compute 0", "video codec engine", "video jpeg 0"}
)


def _pdh_counter_array(counter_path: str, *, wait_s: float = 0.75) -> list[tuple[str, float]]:
    """One PDH wildcard collect. Returns (instance_name, value) pairs."""
    if os.name != "nt":
        return []
    import ctypes
    from ctypes import wintypes

    pdh = ctypes.windll.pdh
    PDH_FMT_DOUBLE = 0x00000200
    PDH_MORE_DATA = 0x800007D2
    PDH_NO_DATA = 0x800007D5

    class PDH_FMT_COUNTERVALUE(ctypes.Structure):
        _fields_ = [
            ("CStatus", wintypes.DWORD),
            ("_pad", wintypes.DWORD),
            ("doubleValue", ctypes.c_double),
        ]

    class PDH_FMT_COUNTERVALUE_ITEM(ctypes.Structure):
        _fields_ = [
            ("szName", wintypes.LPWSTR),
            ("FmtValue", PDH_FMT_COUNTERVALUE),
        ]

    h_query = wintypes.HANDLE()
    if pdh.PdhOpenQueryW(None, None, ctypes.byref(h_query)) != 0:
        return []
    try:
        h_counter = wintypes.HANDLE()
        add = pdh.PdhAddEnglishCounterW(
            h_query, counter_path, None, ctypes.byref(h_counter)
        )
        if add != 0:
            # Localized install fallback.
            if pdh.PdhAddCounterW(h_query, counter_path, None, ctypes.byref(h_counter)) != 0:
                return []
        pdh.PdhCollectQueryData(h_query)
        time.sleep(max(0.2, wait_s))
        pdh.PdhCollectQueryData(h_query)

        def _read_array() -> list[tuple[str, float]]:
            buf_size = wintypes.DWORD(0)
            item_count = wintypes.DWORD(0)
            st = pdh.PdhGetFormattedCounterArrayW(
                h_counter,
                PDH_FMT_DOUBLE,
                ctypes.byref(buf_size),
                ctypes.byref(item_count),
                None,
            )
            code = st & 0xFFFFFFFF
            if code == PDH_NO_DATA:
                return []
            if buf_size.value <= 0:
                return []
            # First call is usually MORE_DATA with the required buffer size.
            if code not in (0, PDH_MORE_DATA):
                return []
            buf = (ctypes.c_byte * buf_size.value)()
            st = pdh.PdhGetFormattedCounterArrayW(
                h_counter,
                PDH_FMT_DOUBLE,
                ctypes.byref(buf_size),
                ctypes.byref(item_count),
                buf,
            )
            if (st & 0xFFFFFFFF) != 0 or item_count.value <= 0:
                return []
            items = ctypes.cast(buf, ctypes.POINTER(PDH_FMT_COUNTERVALUE_ITEM))
            out: list[tuple[str, float]] = []
            for i in range(item_count.value):
                item = items[i]
                if item.FmtValue.CStatus != 0 or not item.szName:
                    continue
                out.append((str(item.szName), float(item.FmtValue.doubleValue)))
            return out

        out = _read_array()
        if not out:
            # Second beat — first PDH sample is often empty.
            time.sleep(0.35)
            pdh.PdhCollectQueryData(h_query)
            out = _read_array()
        return out
    except Exception:
        return []
    finally:
        pdh.PdhCloseQuery(h_query)


def _parse_gpu_instance(name: str) -> tuple[str, str] | None:
    """pid_…_luid_HI_LO_phys_N_eng_N_engtype_TYPE → (luid, engtype)."""
    low = name.lower()
    if "luid_" not in low or "engtype_" not in low:
        return None
    try:
        after = low.split("luid_", 1)[1]
        parts = after.split("_")
        # luid_0x00000000_0x0001157F_phys_…
        luid = f"{parts[0]}_{parts[1]}"
        eng = low.split("engtype_", 1)[1].strip()
    except (IndexError, ValueError):
        return None
    return luid, eng


def gpu_pct() -> float | None:
    """Radeon 840M utilization. None if not sampled.

    Linux: amdgpu gpu_busy_percent. Windows: GPU Engine PDH, cached.
    """
    if os.name != "nt":
        return _linux_gpu_pct()
    global _gpu_cache
    now = time.time()
    ts, cached = _gpu_cache
    if now - ts < _GPU_CACHE_S and cached is not None:
        return cached
    rows = _pdh_counter_array(r"\GPU Engine(*)\Utilization Percentage", wait_s=0.75)
    if not rows:
        # Do not bump success timestamp on miss — allow a quick retry next call.
        # Keep a recent last-good briefly so jsonl does not go blank on one miss.
        if cached is not None and now - ts < 90.0:
            return cached
        return None
    by_luid_eng: dict[tuple[str, str], float] = {}
    engines_by_luid: dict[str, set[str]] = {}
    for name, val in rows:
        parsed = _parse_gpu_instance(name)
        if parsed is None:
            continue
        luid, eng = parsed
        engines_by_luid.setdefault(luid, set()).add(eng)
        key = (luid, eng)
        by_luid_eng[key] = by_luid_eng.get(key, 0.0) + max(0.0, val)

    def score(luid: str) -> int:
        names = engines_by_luid.get(luid) or set()
        return sum(1 for n in names if n in _GPU_ENGINE_TYPES or n.startswith("compute"))

    if not engines_by_luid:
        if cached is not None and now - ts < 90.0:
            return cached
        return None
    luid = max(engines_by_luid, key=score)
    useful = [
        v
        for (lu, eng), v in by_luid_eng.items()
        if lu == luid and (eng in _GPU_ENGINE_TYPES or eng.startswith("compute"))
    ]
    if not useful:
        if cached is not None and now - ts < 90.0:
            return cached
        return None
    pct = round(min(100.0, max(useful)), 1)
    _gpu_cache = (now, pct)
    return pct


def _npu_via_get_counter() -> float | None:
    """PowerShell Get-Counter fallback. None when the NPU object is missing."""
    if os.name != "nt":
        return None
    ps = shutil.which("powershell") or shutil.which("pwsh")
    if not ps:
        return None
    script = (
        "$ErrorActionPreference='Stop'; "
        "try { "
        "$c=Get-Counter -Counter '\\NPU Engine(*)\\Utilization Percentage' "
        "-SampleInterval 1 -MaxSamples 1; "
        "($c.CounterSamples | Measure-Object -Property CookedValue -Maximum).Maximum "
        "} catch { '' }"
    )
    try:
        out = subprocess.run(
            [ps, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
            capture_output=True,
            text=True,
            timeout=8,
            creationflags=CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
    except Exception:
        return None
    raw = (out.stdout or "").strip().splitlines()
    if not raw:
        return None
    try:
        return round(min(100.0, max(0.0, float(raw[-1]))), 1)
    except ValueError:
        return None


def npu_pct() -> float | None:
    """NPU utilization when the OS exposes it. None when not sampled.

    Linux: DRM fdinfo busy time on amdxdna. Idle with the device present is 0%.
    Stock Ollama GGUF does not drive XDNA.
    """
    global _npu_cache
    now = time.time()
    ts, cached = _npu_cache
    if cached is not None and now - ts < _GPU_CACHE_S:
        return cached
    if os.name != "nt":
        pct = _linux_npu_pct()
        _npu_cache = (now, pct)
        return pct
    if cached is None and now - ts < _NPU_MISS_RETRY_S and ts > 0:
        return None
    for path in (
        r"\NPU Engine(*)\Utilization Percentage",
        r"\NPU(*)\Utilization Percentage",
    ):
        rows = _pdh_counter_array(path, wait_s=0.4)
        if rows:
            vals = [max(0.0, v) for _n, v in rows]
            if vals:
                pct = round(min(100.0, max(vals)), 1)
                _npu_cache = (now, pct)
                return pct
    got = _npu_via_get_counter()
    if got is not None:
        _npu_cache = (now, got)
        return got
    _npu_cache = (now, None)
    return None


_NET_SKIP = ("lo", "docker", "veth", "br-", "virbr", "tun", "tap", "wg")
_AUTH_FAIL = re.compile(
    r"failed password|invalid user|failed publickey|authentication failure",
    re.I,
)
_AUTH_LOGS = (Path("/var/log/auth.log"), Path("/var/log/auth.log.1"))


def _skip_iface(name: str) -> bool:
    n = (name or "").lower()
    return n == "lo" or any(n.startswith(p) for p in _NET_SKIP)


def default_net_iface() -> str | None:
    path = Path("/proc/net/route")
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()[1:]
    except OSError:
        return None
    for line in lines:
        parts = line.split()
        if len(parts) < 2:
            continue
        if parts[1] == "00000000" and not _skip_iface(parts[0]):
            return parts[0]
    return None


def net_counters() -> dict[str, Any] | None:
    """Wi-Fi / Ethernet byte counters. Skip loopback and virtual bridges."""
    try:
        pernic = psutil.net_io_counters(pernic=True) or {}
    except Exception:
        return None
    iface = default_net_iface()
    if iface not in pernic:
        named = [n for n in pernic if not _skip_iface(n)]
        iface = named[0] if named else None
    if not iface or iface not in pernic:
        return None
    row = pernic[iface]
    return {
        "iface": iface,
        "link": "wireless" if iface.startswith(("wl", "wlan")) else "wired",
        "rx": int(row.bytes_recv or 0),
        "tx": int(row.bytes_sent or 0),
        "at": int(time.time() * 1000),
    }


def net_history_path() -> Path:
    try:
        from apps.core import config

        return Path(config.STATE_DIR) / "host-net.jsonl"
    except Exception:
        return Path.home() / ".ollama" / "skills" / "state" / "store" / "host-net.jsonl"


def _append_net_sample(net: dict[str, Any]) -> None:
    path = net_history_path()
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        rec = {
            "at": int(net["at"]),
            "iface": net["iface"],
            "rx": int(net["rx"]),
            "tx": int(net["tx"]),
        }
        with path.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(rec, separators=(",", ":")) + "\n")
    except OSError:
        return
    try:
        if path.stat().st_size < 1_500_000:
            return
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()[-4000:]
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    except OSError:
        return


def _load_net_rows(path: Path | None = None) -> list[dict[str, Any]]:
    path = path or net_history_path()
    if not path.is_file():
        return []
    out: list[dict[str, Any]] = []
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []
    for line in lines:
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        try:
            out.append(
                {
                    "at": int(row["at"]),
                    "iface": str(row.get("iface") or ""),
                    "rx": int(row["rx"]),
                    "tx": int(row["tx"]),
                }
            )
        except (KeyError, TypeError, ValueError):
            continue
    out.sort(key=lambda r: r["at"])
    return out


def _row_at_or_before(rows: list[dict[str, Any]], target_ms: int) -> dict[str, Any] | None:
    prev = None
    for row in rows:
        if int(row["at"]) <= target_ms:
            prev = row
        else:
            break
    return prev


def net_usage_window(
    seconds: int,
    *,
    now: dict[str, Any] | None = None,
    rows: list[dict[str, Any]] | None = None,
    min_cover: float = 0.75,
) -> dict[str, Any] | None:
    """Byte delta for a lookback window. None until enough live samples exist."""
    now = now or net_counters()
    if not now:
        return None
    series = rows if rows is not None else _load_net_rows()
    if not series:
        return None
    now_at = int(now["at"])
    need_ms = int(seconds * min_cover * 1000)
    then = _row_at_or_before(series, now_at - int(seconds * 1000))
    if then is None:
        oldest = series[0]
        if now_at - int(oldest["at"]) < need_ms:
            return None
        then = oldest
    cover_ms = now_at - int(then["at"])
    if cover_ms < need_ms:
        return None
    if now["iface"] and then.get("iface") and now["iface"] != then["iface"]:
        return None
    rx = int(now["rx"]) - int(then["rx"])
    tx = int(now["tx"]) - int(then["tx"])
    if rx < 0 or tx < 0:
        return None
    return {
        "seconds": int(cover_ms / 1000),
        "rx": rx,
        "tx": tx,
        "total": rx + tx,
        "iface": now.get("iface"),
        "link": now.get("link"),
    }


def spoken_bytes(n: int) -> str:
    n = max(0, int(n))
    if n < 1024:
        return f"{n} bytes"
    kb = n / 1024.0
    if kb < 1024:
        return f"{kb:.1f} kilobytes"
    mb = kb / 1024.0
    if mb < 1024:
        return f"{mb:.1f} megabytes"
    return f"{mb / 1024.0:.2f} gigabytes"


def bandwidth_spoken(now: datetime | None = None) -> str | None:
    from zoneinfo import ZoneInfo

    from apps.voice.speakable import spoken_clock

    net = net_counters()
    if net:
        _append_net_sample(net)
    hour = net_usage_window(3600, now=net)
    day = net_usage_window(86400, now=net)
    if hour is None and day is None:
        return None
    hst = now or datetime.now(ZoneInfo("Pacific/Honolulu"))
    link = (net or {}).get("link") or "network"
    bits = [
        f"Bandwidth desk at {spoken_clock(hst.hour, hst.minute)} Hawaiian Standard Time.",
        f"This host is on {link}.",
    ]
    if hour:
        bits.append(
            f"Last hour: {spoken_bytes(hour['rx'])} down, {spoken_bytes(hour['tx'])} up, "
            f"{spoken_bytes(hour['total'])} total."
        )
    else:
        bits.append("Last hour is not on file yet.")
    if day:
        bits.append(
            f"Last twenty four hours: {spoken_bytes(day['rx'])} down, {spoken_bytes(day['tx'])} up, "
            f"{spoken_bytes(day['total'])} total."
        )
    else:
        bits.append("Last twenty four hours is not on file yet.")
    return " ".join(bits)


def _auth_fail_counts(now_s: float | None = None) -> dict[str, int]:
    now_s = now_s if now_s is not None else time.time()
    hour = now_s - 3600
    day = now_s - 86400
    c1 = c24 = 0
    for path in _AUTH_LOGS:
        if not path.is_file():
            continue
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue
        for line in lines:
            if not _AUTH_FAIL.search(line):
                continue
            ts = None
            head = line.split(" ", 1)[0]
            try:
                ts = datetime.fromisoformat(head).timestamp()
            except ValueError:
                continue
            if ts >= hour:
                c1 += 1
            if ts >= day:
                c24 += 1
    return {"failed_1h": c1, "failed_24h": c24}


def _ufw_start_on_boot() -> bool | None:
    path = Path("/etc/ufw/ufw.conf")
    if not path.is_file():
        return None
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None
    for line in text.splitlines():
        if line.strip().upper().startswith("ENABLED="):
            return line.split("=", 1)[1].strip().lower() in {"yes", "true", "1"}
    return None


def _unit_active(name: str) -> bool | None:
    exe = shutil.which("systemctl")
    if not exe:
        return None
    try:
        out = subprocess.run(
            [exe, "is-active", name],
            capture_output=True,
            text=True,
            timeout=2,
        )
    except Exception:
        return None
    val = (out.stdout or "").strip().lower()
    if val in {"active", "inactive", "failed"}:
        return val == "active"
    return None


def _listen_established() -> tuple[int | None, int | None]:
    try:
        conns = psutil.net_connections(kind="inet")
    except Exception:
        return None, None
    listen = sum(1 for c in conns if str(c.status) == "LISTEN")
    est = sum(1 for c in conns if str(c.status) == "ESTABLISHED")
    return listen, est


def security_snapshot() -> dict[str, Any]:
    fails = _auth_fail_counts()
    listen, est = _listen_established()
    ssh = _unit_active("ssh")
    if ssh is None:
        ssh = _unit_active("sshd")
    return {
        "failed_1h": fails["failed_1h"],
        "failed_24h": fails["failed_24h"],
        "listen_tcp": listen,
        "established": est,
        "ufw_boot": _ufw_start_on_boot(),
        "ssh_active": ssh,
        "fail2ban": Path("/var/run/fail2ban/fail2ban.pid").is_file(),
    }


def security_spoken(now: datetime | None = None) -> str | None:
    from zoneinfo import ZoneInfo

    from apps.voice.speakable import spoken_clock

    row = security_snapshot()
    hst = now or datetime.now(ZoneInfo("Pacific/Honolulu"))
    bits = [
        f"Security desk at {spoken_clock(hst.hour, hst.minute)} Hawaiian Standard Time.",
    ]
    ufw = row.get("ufw_boot")
    if ufw is True:
        bits.append("Uncomplicated Firewall is set to start on boot.")
    elif ufw is False:
        bits.append("Uncomplicated Firewall is not set to start on boot.")
    ssh = row.get("ssh_active")
    if ssh is True:
        bits.append("OpenSSH service is active.")
    elif ssh is False:
        bits.append("OpenSSH service is not active.")
    if row.get("listen_tcp") is not None:
        bits.append(f"{row['listen_tcp']} TCP listeners.")
    if row.get("established") is not None:
        bits.append(f"{row['established']} established connections.")
    bits.append(
        f"Failed sign-ins: {row['failed_1h']} in the last hour, "
        f"{row['failed_24h']} in the last twenty four hours."
    )
    if row.get("fail2ban"):
        bits.append("Fail2ban is running.")
    return " ".join(bits)


def snapshot(*, home: Path | None = None) -> dict[str, Any]:
    """One live host sample. Safe to persist as jsonl."""
    cpu = float(psutil.cpu_percent(interval=0.1))
    mem = psutil.virtual_memory()
    temp_c, temp_src = host_temp_c()
    batt = host_battery()
    disk_pct = host_disk_pct(home)
    now = time.time()
    row: dict[str, Any] = {
        "at": int(now * 1000),
        "cpu_pct": round(cpu, 2),
        "mem_pct": round(float(mem.percent), 2),
        "mem_used_gb": round(mem.used / (1024 ** 3), 1),
        "mem_total_gb": round(mem.total / (1024 ** 3), 1),
        "uptime_s": int(now - psutil.boot_time()),
        "cpu_count": psutil.cpu_count() or 1,
    }
    if temp_c is not None:
        row["temp_c"] = temp_c
        if temp_src:
            row["temp_src"] = temp_src
    if batt:
        row["battery_pct"] = batt["pct"]
        row["battery_plugged"] = batt["plugged"]
        if "secsleft" in batt:
            row["battery_secsleft"] = batt["secsleft"]
    if disk_pct is not None:
        row["disk_pct"] = disk_pct
    name = gpu_name()
    if name:
        row["gpu_name"] = name
    igpu = gpu_pct()
    if igpu is not None:
        row["gpu_pct"] = igpu
    npu = npu_pct()
    if npu is not None:
        row["npu_pct"] = npu
    row["npu_present"] = npu_present()
    net = net_counters()
    if net:
        row["net_iface"] = net["iface"]
        row["net_rx_bytes"] = net["rx"]
        row["net_tx_bytes"] = net["tx"]
        _append_net_sample(net)
    return row
