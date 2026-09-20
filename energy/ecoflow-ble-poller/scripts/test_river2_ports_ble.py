#!/usr/bin/env python3
"""BLE on/off test for every River 2 Pro port and switch. Prints a result table.

Restores each control to its starting state after the off/on cycle.
Leaves AC on at the end (laptop).

  systemctl --user stop ava-ecoflow-ble
  ~/.ollama/skills/origin/.venv/bin/python \\
    ~/.ollama/skills/ecoflow-ble-poller/scripts/test_river2_ports_ble.py
  systemctl --user start ava-ecoflow-ble

If auth fails with NeedBindInstallFirst: open the EcoFlow phone app, bind this
River to the same account as AVA_ECOFLOW_USER_ID, close the app, retry.
Keep the phone EcoFlow app closed while the test runs.
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

from ecoflow_ble_store import RIVER_SN  # noqa: E402

DEFAULT_MAC = "DC:06:75:56:AC:1D"
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
    getter: str | None
    setter: str
    note: str = ""
    settle: float = 0.0


# Every BLE switch eflib exposes on River 2 Pro
CASES = [
    Case("ac_ports", "ac_ports", "enable_ac_ports", "AC master — laptop", settle=5.0),
    Case("dc_12v_port", "dc_12v_port", "enable_dc_12v_port", "car / cigarette 12V"),
    Case("ac_xboost", "ac_xboost", "enable_ac_xboost", "X-Boost"),
    Case("energy_backup", "energy_backup", "enable_energy_backup", "reserve SOC mode"),
    Case(
        "ac_always_on",
        None,
        "enable_ac_always_on",
        "no live getter — command-only check",
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
    from eflib.devices.river2_pro import Device

    rec = await _scan(mac)
    if rec is None:
        raise RuntimeError("not_advertising")
    ble, adv = rec
    if MFG_KEY not in (adv.manufacturer_data or {}):
        raise RuntimeError("missing_manufacturer_data")
    device = NewDevice(ble, adv)
    if device is None or not isinstance(device, Device):
        device = Device(ble, adv, RIVER_SN)
    await device.connect(user_id=user_id, max_attempts=3)
    try:
        state = await asyncio.wait_for(
            device.wait_until_authenticated_or_error(raise_on_error=True),
            timeout=45,
        )
    except Exception as e:
        try:
            await device.disconnect()
        except Exception:
            pass
        name = type(e).__name__
        if "NeedBind" in name or "NeedBind" in str(e):
            raise RuntimeError(
                "NeedBindInstallFirst — open EcoFlow phone app, bind this River "
                "to the same account as AVA_ECOFLOW_USER_ID, close the app, retry"
            ) from e
        raise RuntimeError(f"auth_failed: {name}: {e}") from e
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

    wait = case.settle or settle

    if case.getter is None:
        try:
            await setter(False)
            await _settle(wait)
            await setter(True)
            await _settle(wait)
            await setter(False)
            await _settle(wait)
            row["start"] = "n/a"
            row["off"] = "sent"
            row["on"] = "sent"
            row["restored"] = "sent"
            row["result"] = "PASS"
            row["detail"] = "commands_ok_no_verify"
        except Exception as e:
            row["detail"] = f"{type(e).__name__}: {e}"
        return device, row

    start = await _wait_field(device, case.getter)
    if start is None:
        row["detail"] = "no_live_state"
        return device, row
    start = bool(start)
    row["start"] = start
    try:
        await setter(False)
        await _settle(wait)
        device = await _ensure(device, mac, user_id)
        off = bool(await _wait_field(device, case.getter) or False)
        row["off"] = off

        await setter(True)
        await _settle(wait)
        device = await _ensure(device, mac, user_id)
        on = bool(await _wait_field(device, case.getter) or False)
        row["on"] = on

        await setter(start)
        await _settle(wait)
        device = await _ensure(device, mac, user_id)
        restored = bool(await _wait_field(device, case.getter) or False)
        row["restored"] = restored

        if off is False and on is True and restored is start:
            row["result"] = "PASS"
            row["detail"] = "off→on→restored"
        else:
            bits = []
            if off is not False:
                bits.append(f"off={off}")
            if on is not True:
                bits.append(f"on={on}")
            if restored is not start:
                bits.append(f"restored={restored} want={start}")
            row["detail"] = " ".join(bits) or "mismatch"
    except Exception as e:
        row["detail"] = f"{type(e).__name__}: {e}"
        try:
            device = await _ensure(device, mac, user_id)
            await setter(start)
        except Exception:
            pass
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
    passed = sum(1 for r in rows if r["result"] == "PASS")
    print(f"\n{passed}/{len(rows)} PASS")


async def main() -> int:
    p = argparse.ArgumentParser(description="River 2 Pro BLE port/feature on-off test")
    p.add_argument("--mac", default="")
    p.add_argument("--settle", type=float, default=3.0)
    p.add_argument("--only", default="", help="comma list (default: all)")
    args = p.parse_args()

    _load_env()
    user_id = (os.getenv("AVA_ECOFLOW_USER_ID") or "").strip()
    mac = (args.mac or os.getenv("AVA_ECOFLOW_RIVER_BLE_MAC") or DEFAULT_MAC).strip()
    if not user_id.isdigit():
        print("FAIL missing AVA_ECOFLOW_USER_ID in origin/.env")
        return 2

    print(f"River 2 Pro BLE test  sn=…{RIVER_SN[-6:]}  mac={mac}")
    print("Testing EVERY port/switch including AC. Laptop will drop briefly; AC left on at end.")

    device = None
    rows: list[dict] = []
    try:
        device = await _connect(mac, user_id)
        before = _snap(
            device,
            [
                "battery_level",
                "ac_ports",
                "dc_12v_port",
                "ac_xboost",
                "energy_backup",
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
        _print_table(rows, "River 2 Pro BLE port / feature results")
        return 0 if rows and all(r["result"] == "PASS" for r in rows) else 1
    except Exception as e:
        print(f"FAIL {e}")
        return 5
    finally:
        if device is not None:
            try:
                device = await _ensure(device, mac, user_id)
                await device.enable_ac_ports(True)
                await _settle(3.0)
                print("BLE AC leave-on:", getattr(device, "ac_ports", None))
            except Exception as e:
                print("BLE AC leave-on failed:", e)
            try:
                await device.disconnect()
            except Exception:
                pass


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
