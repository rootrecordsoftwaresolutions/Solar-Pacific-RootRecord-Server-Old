#!/usr/bin/env python3
"""One-shot Delta 2 USB-C toggle. Never touches AC."""
from __future__ import annotations

import asyncio
import os
import sys
from pathlib import Path

OPS = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store"
AVA = Path.home() / "RootRecord" / "Ava-Core"
sys.path.insert(0, str(OPS / "vendor"))
sys.path.insert(0, str(OPS))

from ecoflow_ble_store import DELTA_SN  # noqa: E402

DEFAULT_DELTA_MAC = "24:58:7C:20:92:61"


def _load_env() -> None:
    env = AVA / ".env"
    if not env.is_file():
        return
    for line in env.read_text(encoding="utf-8", errors="replace").splitlines():
        s = line.strip()
        if not s or s.startswith("#") or "=" not in s:
            continue
        k, _, v = s.partition("=")
        k, v = k.strip(), v.strip().strip('"').strip("'")
        if k and k not in os.environ:
            os.environ[k] = v


def _snap(device) -> dict:
    return {
        "usb": bool(getattr(device, "usb_ports", False)),
        "ac": bool(getattr(device, "ac_ports", False)),
        "usb_c_w": getattr(device, "usbc_output_power", None),
    }


async def main() -> int:
    _load_env()
    user_id = os.getenv("AVA_ECOFLOW_USER_ID", "").strip()
    mac = (os.getenv("AVA_ECOFLOW_BLE_MAC") or DEFAULT_DELTA_MAC).strip() or DEFAULT_DELTA_MAC
    if not user_id.isdigit():
        print("FAIL missing_user_id")
        return 2
    from bleak import BleakScanner
    from eflib import NewDevice
    from eflib.connection import ConnectionState
    from eflib.devices.delta2 import Device

    want = mac.upper()
    hit: dict = {}

    def _cb(d, adv):
        if d.address.upper() == want:
            hit["rec"] = (d, adv)

    scanner = BleakScanner(detection_callback=_cb)
    await scanner.start()
    await asyncio.sleep(10)
    await scanner.stop()
    await asyncio.sleep(0.3)
    if "rec" not in hit:
        print("FAIL not_advertising")
        return 3
    ble, adv = hit["rec"]
    if 0xB5B5 not in (adv.manufacturer_data or {}):
        print("FAIL missing EcoFlow manufacturer data")
        return 3
    device = NewDevice(ble, adv)
    if device is None or not isinstance(device, Device):
        device = Device(ble, adv, DELTA_SN)
    await device.connect(user_id=user_id, max_attempts=3)
    try:
        state = await asyncio.wait_for(
            device.wait_until_authenticated_or_error(raise_on_error=False),
            timeout=45,
        )
        if state != ConnectionState.AUTHENTICATED:
            print(f"FAIL auth {state}")
            return 4
        await asyncio.sleep(2)
        before = _snap(device)
        ac0 = before["ac"]
        start_usb = before["usb"]
        target = not start_usb
        await device.enable_usb_ports(target)
        await asyncio.sleep(2)
        mid = _snap(device)
        if bool(mid["ac"]) != ac0:
            print(f"FAIL ac_changed before_ac={ac0} mid_ac={mid['ac']}")
            return 5
        if bool(mid["usb"]) != target:
            print(f"FAIL usb_not_toggled start={start_usb} mid={mid['usb']} want={target}")
            return 6
        await device.enable_usb_ports(start_usb)
        await asyncio.sleep(2)
        after = _snap(device)
        if bool(after["ac"]) != ac0:
            print(f"FAIL ac_changed_on_restore ac={after['ac']}")
            return 7
        if bool(after["usb"]) != start_usb:
            print(f"FAIL usb_not_restored start={start_usb} after={after['usb']}")
            return 8
        print(
            "OK usb_toggle "
            f"start_usb={start_usb} flipped={mid['usb']} restored={after['usb']} "
            f"ac_unchanged={ac0}"
        )
        return 0
    finally:
        try:
            await device.disconnect()
        except Exception:
            pass


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
