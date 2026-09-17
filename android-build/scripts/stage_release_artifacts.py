#!/usr/bin/env python3
"""Copy signed/debug APK + AAB into ~/.local/opt/rootrecord/android-builds/<app>."""
from __future__ import annotations

import argparse
import shutil
from pathlib import Path


def find_outputs(app_dir: Path, release: bool) -> list[Path]:
    kind = "release" if release else "debug"
    roots = [
        app_dir / "app" / "build" / "outputs" / "apk" / kind,
        app_dir / "app" / "build" / "outputs" / "bundle" / kind,
    ]
    out: list[Path] = []
    for root in roots:
        if not root.is_dir():
            continue
        for p in root.rglob("*"):
            if p.suffix.lower() in {".apk", ".aab"} and p.is_file():
                out.append(p)
    return out


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--app-dir", required=True, type=Path)
    p.add_argument("--dest-dir", required=True, type=Path)
    p.add_argument("--base-name", required=True)
    p.add_argument("--version", required=True)
    p.add_argument("--release", action="store_true")
    args = p.parse_args()
    dest = args.dest_dir.resolve()
    dest.mkdir(parents=True, exist_ok=True)
    files = find_outputs(args.app_dir.resolve(), args.release)
    if not files:
        raise SystemExit(f"no apk/aab under {args.app_dir}")
    for src in files:
        dest_name = f"{args.base_name}-{args.version}{src.suffix.lower()}"
        shutil.copy2(src, dest / dest_name)
        print(dest / dest_name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
