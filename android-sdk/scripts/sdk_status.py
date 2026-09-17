#!/usr/bin/env python3
"""Inventory ANDROID_HOME without walking platform data/res."""
from __future__ import annotations

import os
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

HST = ZoneInfo("Pacific/Honolulu")
SKILL = Path(__file__).resolve().parents[1]
SDK = Path.home() / ".local" / "opt" / "android-sdk"
CURRENT = SKILL / "CURRENT.md"
HOME_LINK = Path.home() / "android-sdk"
APPS = (
    Path.home() / ".ollama" / "skills" / "ava-ops" / "android",
    Path.home() / ".ollama" / "skills" / "kilauea-alerts" / "android",
    Path.home() / ".ollama" / "skills" / "rootmc-android" / "android",
)


def now_iso() -> str:
    return datetime.now(HST).isoformat(timespec="seconds")


def children(path: Path) -> list[str]:
    if not path.is_dir():
        return []
    names = []
    with os.scandir(path) as it:
        for e in it:
            names.append(e.name + ("/" if e.is_dir(follow_symlinks=False) else ""))
    return sorted(names)


def main() -> int:
    top = children(SDK)
    platforms = children(SDK / "platforms")
    build_tools = children(SDK / "build-tools")
    ndk = children(SDK / "ndk")
    cmdline = children(SDK / "cmdline-tools")
    sdkmanager = SDK / "cmdline-tools" / "latest" / "bin" / "sdkmanager"
    adb = SDK / "platform-tools" / "adb"
    home_kind = "missing"
    if HOME_LINK.is_symlink():
        home_kind = f"symlink → {os.path.realpath(HOME_LINK)}"
    elif HOME_LINK.is_dir():
        home_kind = "directory (should be symlink)"
    lines = [
        "# android-sdk — generated",
        "",
        f"Generated {now_iso()}. Do not edit by hand. Do not rglob `sdk/`.",
        "",
        f"- ANDROID_HOME: `{SDK}`",
        f"- exists: **{SDK.is_dir()}**",
        f"- `~/android-sdk`: {home_kind}",
        f"- sdkmanager: **{sdkmanager.is_file()}** (`{sdkmanager}`)",
        f"- adb: **{adb.is_file()}**",
        "",
        "## Apps",
        "",
    ]
    for proj in APPS:
        lp = proj / "local.properties"
        sdk_dir_line = ""
        if lp.is_file():
            for line in lp.read_text(encoding="utf-8").splitlines():
                if line.startswith("sdk.dir="):
                    sdk_dir_line = line.split("=", 1)[1].strip()
                    break
        lines.append(
            f"- `{proj}` — exists **{proj.is_dir()}** — sdk.dir `{sdk_dir_line or 'missing'}`"
        )
    lines += [
        "",
        "## Top-level",
        "",
    ]
    if top:
        for n in top:
            lines.append(f"- `{n}`")
    else:
        lines.append("- (empty)")
    lines += ["", "## cmdline-tools", ""]
    lines += [f"- `{n}`" for n in cmdline] or ["- (none)"]
    lines += ["", "## platforms", ""]
    lines += [f"- `{n}`" for n in platforms] or ["- (none)"]
    lines += ["", "## build-tools", ""]
    lines += [f"- `{n}`" for n in build_tools] or ["- (none)"]
    lines += ["", "## ndk", ""]
    lines += [f"- `{n}`" for n in ndk] or ["- (none)"]
    lines += [
        "",
        "Build: source `scripts/android-env.sh`, then `./gradlew assembleDebug` in an app folder.",
        "",
        "Do not walk `sdk/platforms/*/data`. Do not cat licenses or keystores.",
        "",
    ]
    CURRENT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {CURRENT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
