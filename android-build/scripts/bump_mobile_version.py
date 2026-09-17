#!/usr/bin/env python3
"""Bump versionCode + PATCH versionName in an Android Gradle file. Prints new versionName."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def bump(gradle: Path) -> str:
    text = gradle.read_text(encoding="utf-8")
    kts = gradle.suffix == ".kts"
    code_re = re.compile(r"versionCode\s*=\s*(\d+)" if kts else r"versionCode\s+(\d+)")
    name_re = re.compile(r'versionName\s*=\s*"([^"]+)"' if kts else r'versionName\s+"([^"]+)"')
    cm = code_re.search(text)
    nm = name_re.search(text)
    if not cm or not nm:
        raise SystemExit(f"could not find versionCode/versionName in {gradle}")
    old_code = int(cm.group(1))
    old_name = nm.group(1)
    new_code = old_code + 1
    parts = old_name.split(".")
    while len(parts) < 3:
        parts.append("0")
    new_name = f"{parts[0]}.{parts[1]}.{new_code}"
    print(f"bump-mobile-version: {old_name} (code {old_code}) -> {new_name} (code {new_code})", file=sys.stderr)
    if kts:
        text = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {new_code}", text, count=1)
        text = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_name}"', text, count=1)
    else:
        text = re.sub(r"versionCode\s+\d+", f"versionCode {new_code}", text, count=1)
        text = re.sub(r'versionName\s+"[^"]+"', f'versionName "{new_name}"', text, count=1)
    gradle.write_text(text, encoding="utf-8")
    return new_name


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--gradle", required=True, type=Path)
    args = p.parse_args()
    print(bump(args.gradle.resolve()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
