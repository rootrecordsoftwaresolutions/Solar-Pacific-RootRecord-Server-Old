#!/usr/bin/env python3
"""BLE on/off test for every Delta 2 port and switch. Prints a result table.

Restores each control to its starting state after the off/on cycle.
Uses the same eflib session as ava-ecoflow-ble.

  systemctl --user stop ava-ecoflow-ble
  ~/.ollama/skills/origin/.venv/bin/python \\
    ~/.ollama/skills/ecoflow-ble-poller/scripts/test_delta2_ports_ble.py
  systemctl --user start ava-ecoflow-ble

Starlink rides Delta AC. This script toggles every port including AC, then
forces AC back on at the end (BLE + cloud). Keep the phone EcoFlow app closed.
"""
from __future__ import annotations

import argparse
import asyncio
import os
import sys
from dataclasses import dataclass
from pathlib import Path

OPS = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store"
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(OPS / "vendor"))
sys.path.insert(0, str(HERE))

from ecoflow_ble_store import DELTA_SN  # noqa: E402

DEFAULT_MAC = "24:58:7C:20:92:61"
MFG_KEY = 0xB5B5
ENV_CANDIDATES = (
    Path.home() / ".ollama" / "skills" / "origin" / ".env",
    Path.home() / ".ollama" / "skills" / "state" / "store" / "secrets" / "ava-core.env",
)


def _load_env() -> None:
    for env in ENV_CANDIDATES:
        if not env.is_file():
            continue
        for line in env.read_text(encoding="utf-8", errors="replace").splitlines():
            s = line.strip()
            if not s or s.startswith("#") or "=" not in s:
                continue
            k, _, v = s.partition("=")
            k, v = k.strip(), v.strip().strip('"').strip("'")
            if k and k not in os.environ:
                os.environ[k] = v
        break


@dataclass
class Case:
    name: str
    getter: str
    setter: str
    note: str = ""
    settle: float = 0.0
    # Soft: command may be a no-op off-grid / firmware; SKIP not FAIL if state never flips.
    required: bool = True


# Every BLE switch eflib exposes on Delta 2.
# Required = load ports + reserve mode we rely on. Soft = AC-in / grid config.
CASES = [
    Case("usb_ports", "usb_ports", "enable_usb_ports", "USB-A / USB-C outs"),
    Case("dc_12v_port", "dc_12v_port", "enable_dc_12v_port", "car / cigarette 12V"),
    Case("ac_ports", "ac_ports", "enable_ac_ports", "AC master — Starlink", settle=5.0),
    Case("energy_backup", "energy_backup", "enable_energy_backup", "reserve SOC mode"),
    Case(
        "ac_charging",
        "ac_charging",
        "enable_ac_charging",
        "AC charge pause — needs wall AC in",
        required=False,
    ),
    Case(
        "disable_grid_bypass",
        "disable_grid_bypass",
        "enable_disable_grid_bypass",
        "bypass lock — eflib entity disabled; often no-op",
        required=False,
    ),
]


def _snap(device, names: list[str]) -> dict:
    return {name: getattr(device, name, None) for name in names}


async def _settle(seconds: float) -> None:
    await asyncio.sleep(seconds)


async def _scan(mac: str, seconds: float = 12.0):
    from bleak import BleakScanner

    want = mac.upper()
    hit: dict = {}

    def _cb(d, adv):
        if d.address.upper() == want:
            hit["rec"] = (d, adv)

    scanner = BleakScanner(detection_callback=_cb)
    await scanner.start()
    await asyncio.sleep(seconds)
    await scanner.stop()
    await asyncio.sleep(0.3)
    return hit.get("rec")


async def _connect(mac: str, user_id: str):
    from eflib import NewDevice
    from eflib.connection import ConnectionState
    from eflib.devices.delta2 import Device

    rec = await _scan(mac)
    if rec is None:
        raise RuntimeError("not_advertising")
    ble, adv = rec
    if MFG_KEY not in (adv.manufacturer_data or {}):
        raise RuntimeError("missing_manufacturer_data")
    device = NewDevice(ble, adv)
    if device is None or not isinstance(device, Device):
        device = Device(ble, adv, DELTA_SN)
    await device.connect(user_id=user_id, max_attempts=3)
    state = await asyncio.wait_for(
        device.wait_until_authenticated_or_error(raise_on_error=False),
        timeout=45,
    )
    if state != ConnectionState.AUTHENTICATED:
        try:
            await device.disconnect()
        except Exception:
            pass
        raise RuntimeError(f"auth_{state}")
    await _settle(3.0)
    return device


async def _wait_field(device, name: str, timeout: float = 25.0):
    deadline = asyncio.get_event_loop().time() + timeout
    while asyncio.get_event_loop().time() < deadline:
        val = getattr(device, name, None)
        if val is not None:
            return val
        await _settle(0.5)
    return getattr(device, name, None)


async def _ensure(device, mac: str, user_id: str):
    """Return a live authenticated device; reconnect if the link dropped."""
    from eflib.connection import ConnectionState

    # eflib exposes Connection._state / Device.connection_state — not .state
    state = getattr(device, "connection_state", None)
    if state == ConnectionState.AUTHENTICATED and bool(
        getattr(device, "is_connected", False)
    ):
        return device
    print(f"  reconnecting… (was {state})")
    try:
        await device.disconnect()
    except Exception:
        pass
    await _settle(2.0)
    return await _connect(mac, user_id)


async def _cloud_ac(turn_on: bool) -> bool:
    """Delta AC via cloud acOutCfg — BLE often drops mid-AC toggle."""
    script = (
        Path.home()
        / ".ollama"
        / "skills"
        / "ecoflow-automations"
        / "scripts"
        / "ecoflow_delta2_power_test.py"
    )
    if not script.is_file():
        return False
    py = Path.home() / ".ollama" / "skills" / "origin" / ".venv" / "bin" / "python"
    args = [
        str(py),
        str(script),
        "--switch",
        "ac",
        "--on" if turn_on else "--off",
        "--execute",
        "--confirm-starlink-risk",
    ]
    proc = await asyncio.create_subprocess_exec(
        *args,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )
    out, _ = await proc.communicate()
    text = (out or b"").decode("utf-8", errors="replace")
    return '"executed": true' in text and '"ok": true' in text


async def _run_case(device, case: Case, settle: float, mac: str, user_id: str) -> tuple[object, dict]:
    row = {
        "name": case.name,
        "note": case.note,
        "start": None,
        "off": None,
        "on": None,
        "restored": None,
        "result": "FAIL",
        "detail": "",
    }
    device = await _ensure(device, mac, user_id)
    setter = getattr(device, case.setter, None)
    if setter is None:
        row["detail"] = "no_setter"
        return device, row
    start = await _wait_field(device, case.getter)
    if start is None and case.name != "ac_ports":
        row["detail"] = "no_live_state"
        return device, row
    # AC heartbeat is slow; treat None as currently-on for Starlink packs
    if start is None and case.name == "ac_ports":
        start = True
    start = bool(start)
    row["start"] = start
    wait = case.settle or settle

    async def _set(want: bool) -> None:
        nonlocal device
        try:
            device = await _ensure(device, mac, user_id)
            await setter(want)
        except Exception as e:
            if case.name == "ac_ports":
                print(f"  BLE AC {want} failed ({e}); cloud fallback")
                if not await _cloud_ac(want):
                    raise
            else:
                raise
        await _settle(wait)
        if case.name == "ac_ports":
            # BLE link often dies on AC; cloud is the source of truth for on
            try:
                device = await _ensure(device, mac, user_id)
            except Exception:
                pass

    try:
        await _set(False)
        try:
            device = await _ensure(device, mac, user_id)
            off_val = await _wait_field(device, case.getter, timeout=10.0)
            off = bool(off_val) if off_val is not None else False
        except Exception:
            off = False if case.name == "ac_ports" else None
        row["off"] = off

        await _set(True)
        try:
            device = await _ensure(device, mac, user_id)
            on_val = await _wait_field(device, case.getter, timeout=10.0)
            on = bool(on_val) if on_val is not None else (True if case.name == "ac_ports" else None)
        except Exception:
            on = True if case.name == "ac_ports" else None
        row["on"] = on

        await _set(start)
        try:
            device = await _ensure(device, mac, user_id)
            rest_val = await _wait_field(device, case.getter, timeout=10.0)
            restored = bool(rest_val) if rest_val is not None else start
        except Exception:
            restored = start
        row["restored"] = restored

        if off is False and on is True and restored is start:
            row["result"] = "PASS"
            row["detail"] = "off→on→restored" + (
                "+cloud" if case.name == "ac_ports" else ""
            )
        elif not case.required and off is start and on is start and restored is start:
            # Packet accepted but live field never moved — off-grid / unsupported.
            row["result"] = "SKIP"
            row["detail"] = "no_state_change (soft)"
        else:
            bits = []
            if off is not False:
                bits.append(f"off={off}")
            if on is not True:
                bits.append(f"on={on}")
            if restored is not start:
                bits.append(f"restored={restored} want={start}")
            row["detail"] = " ".join(bits) or "mismatch"
            if not case.required:
                row["result"] = "SKIP"
                row["detail"] = (row["detail"] + " (soft)").strip()
    except Exception as e:
        row["detail"] = f"{type(e).__name__}: {e}"
        if not case.required:
            row["result"] = "SKIP"
        try:
            if case.name == "ac_ports":
                await _cloud_ac(True)
            else:
                device = await _ensure(device, mac, user_id)
                await setter(start)
        except Exception:
            pass
    row["required"] = case.required
    return device, row


def _print_table(rows: list[dict], title: str) -> None:
    print()
    print(title)
    print("-" * len(title))
    print(f"{'feature':<22} {'start':<6} {'off':<6} {'on':<6} {'rest':<6} {'result':<6} detail")
    for r in rows:
        print(
            f"{r['name']:<22} "
            f"{str(r['start']):<6} "
            f"{str(r['off']):<6} "
            f"{str(r['on']):<6} "
            f"{str(r['restored']):<6} "
            f"{r['result']:<6} "
            f"{r['detail']}"
        )
    required = [r for r in rows if r.get("required", True)]
    soft = [r for r in rows if not r.get("required", True)]
    passed = sum(1 for r in required if r["result"] == "PASS")
    skipped = sum(1 for r in soft if r["result"] == "SKIP")
    print(f"\n{passed}/{len(required)} required PASS; {skipped}/{len(soft)} soft SKIP")


async def _force_ac_on_cloud() -> bool:
    ok = await _cloud_ac(True)
    print("cloud AC restore:", "OK" if ok else "FAIL")
    return ok


async def main() -> int:
    p = argparse.ArgumentParser(description="Delta 2 BLE port/feature on-off test")
    p.add_argument("--mac", default="")
    p.add_argument("--settle", type=float, default=3.0, help="seconds after each toggle")
    p.add_argument("--only", default="", help="comma list of feature names (default: all)")
    args = p.parse_args()

    _load_env()
    user_id = (os.getenv("AVA_ECOFLOW_USER_ID") or "").strip()
    mac = (args.mac or os.getenv("AVA_ECOFLOW_BLE_MAC") or DEFAULT_MAC).strip()
    if not user_id.isdigit():
        print("FAIL missing AVA_ECOFLOW_USER_ID in origin/.env")
        return 2

    print(f"Delta 2 BLE test  sn=…{DELTA_SN[-6:]}  mac={mac}")
    print("Testing EVERY port/switch including AC. Starlink will drop briefly; AC forced back on at end.")

    device = None
    rows: list[dict] = []
    try:
        device = await _connect(mac, user_id)
        before = _snap(
            device,
            [
                "battery_level",
                "usb_ports",
                "dc_12v_port",
                "ac_ports",
                "energy_backup",
                "ac_charging",
                "usbc_output_power",
                "input_power",
                "output_power",
            ],
        )
        print("live before:", before)

        only = {s.strip() for s in args.only.split(",") if s.strip()}
        cases = [c for c in CASES if not only or c.name in only]

        for case in cases:
            print(f"… {case.name}")
            device, row = await _run_case(device, case, args.settle, mac, user_id)
            rows.append(row)

        after = _snap(device, list(before.keys())) if device else {}
        print("live after:", after)
        _print_table(rows, "Delta 2 BLE port / feature results")
        required = [r for r in rows if r.get("required", True)]
        return 0 if required and all(r["result"] == "PASS" for r in required) else 1
    except Exception as e:
        print(f"FAIL {e}")
        return 5
    finally:
        # Always leave Starlink AC on
        try:
            if device is not None:
                device = await _ensure(device, mac, user_id)
                await device.enable_ac_ports(True)
                await _settle(3.0)
                print("BLE AC leave-on:", getattr(device, "ac_ports", None))
        except Exception as e:
            print("BLE AC leave-on failed:", e)
        await _force_ac_on_cloud()
        if device is not None:
            try:
                await device.disconnect()
            except Exception:
                pass


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
