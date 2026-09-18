#!/usr/bin/env python3
"""One-shot River car DC session for the Night Owl solar cam.

Laptop only flips power — no GIF compile. Uses ecoflow-river-car mpptCar (never AC).
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent
RIVER = Path.home() / ".ollama" / "skills" / "ecoflow-river-car" / "scripts"
for p in (str(ROOT), str(RIVER)):
    if p not in sys.path:
        sys.path.insert(0, p)

from panels_grab import drives_hold_car, set_car_power  # noqa: E402
import river_car_dc as river  # noqa: E402


def power_on(*, execute: bool = True, settle_s: float = 8.0) -> dict[str, Any]:
    was_on = river.car_already_on() is True
    out = set_car_power(want_on=True, execute=bool(execute))
    we = bool(execute) and not was_on and bool(out.get("ok"))
    if we and settle_s > 0:
        time.sleep(float(settle_s))
    return {
        "ok": bool(out.get("ok")) if execute else True,
        "action": "on",
        "was_already_on": was_on,
        "we_powered": we,
        "execute": bool(execute),
        "car": {k: out.get(k) for k in ("ok", "action", "verify_car_on", "put", "wanted")},
    }


def power_off(*, execute: bool = True, we_powered: bool | None = None) -> dict[str, Any]:
    if drives_hold_car():
        return {"ok": True, "action": "off", "skipped": "drives_hold", "execute": bool(execute)}
    if we_powered is False:
        return {"ok": True, "action": "off", "skipped": "not_our_power"}
    out = river.set_car(want_on=False, execute=bool(execute), force=True)
    return {
        "ok": bool(out.get("ok")) if execute else True,
        "action": "off",
        "execute": bool(execute),
        "car": {k: out.get(k) for k in ("ok", "action", "verify_car_on", "put", "left_on")},
    }


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--on", action="store_true")
    ap.add_argument("--off", action="store_true")
    ap.add_argument("--status", action="store_true")
    ap.add_argument("--execute", action="store_true")
    ap.add_argument("--settle", type=float, default=8.0)
    ap.add_argument("--we-powered", choices=("yes", "no", "auto"), default="auto")
    args = ap.parse_args(argv)
    if args.status or (not args.on and not args.off):
        print(json.dumps(river.status(live=True), indent=2))
        return 0
    if args.on and args.off:
        print("pick --on or --off", file=sys.stderr)
        return 2
    if args.on:
        print(json.dumps(power_on(execute=args.execute, settle_s=args.settle), indent=2))
        return 0
    we = None if args.we_powered == "auto" else (args.we_powered == "yes")
    print(json.dumps(power_off(execute=args.execute, we_powered=we), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
