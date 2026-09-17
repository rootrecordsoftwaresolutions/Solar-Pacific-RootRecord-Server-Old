#!/usr/bin/env bash
# Build one Android app on this OmniBook. Usage:
#   assemble.sh <ava-ops|kilauea-alerts|rootmc-android> [--release] [--bump]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "$HERE/android-env.sh"
python3 - "$HERE" "$@" <<'PY'
import json, subprocess, sys
from pathlib import Path

here = Path(sys.argv[1])
args = sys.argv[2:]
if not args:
    raise SystemExit("usage: assemble.sh <app> [--release] [--bump]")
name = args[0]
flags = set(args[1:])
cfg = json.loads((here / "apps.json").read_text(encoding="utf-8"))
app = cfg["apps"].get(name)
if not app:
    raise SystemExit(f"unknown app {name}; known: {', '.join(cfg['apps'])}")
gradle_root = Path(app["gradle"])
gradlew = gradle_root / "gradlew"
if not gradlew.is_file():
    raise SystemExit(f"missing {gradlew}")
version = "debug"
if "--bump" in flags:
    version = subprocess.check_output(
        [sys.executable, str(here / "bump_mobile_version.py"), "--gradle", str(gradle_root / "app" / "build.gradle.kts")],
        text=True,
    ).strip()
task = "assembleRelease" if "--release" in flags else "assembleDebug"
gradle_cmd = [str(gradlew), "--no-daemon"]
if "--release" in flags:
    subprocess.check_call(gradle_cmd + ["--stop"], cwd=gradle_root)
    subprocess.check_call(gradle_cmd + ["bundleRelease", "assembleRelease"], cwd=gradle_root)
else:
    subprocess.check_call(gradle_cmd + [task], cwd=gradle_root)
subprocess.call([str(gradlew), "--stop"], cwd=gradle_root, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
if "--release" in flags:
    subprocess.check_call(
        [
            sys.executable,
            str(here / "stage_release_artifacts.py"),
            "--app-dir",
            str(gradle_root),
            "--dest-dir",
            str(Path(cfg["stage_root"]) / name),
            "--base-name",
            app["base_name"],
            "--version",
            version if version != "debug" else "unsigned",
            "--release",
        ]
    )
print("ok", name, task, version)
PY
