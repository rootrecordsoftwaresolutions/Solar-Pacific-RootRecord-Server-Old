#!/usr/bin/env python3
"""Self-checking RootRecord Ops desk bootstrap."""
from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / ".runtime"
LOG_DIR = RUNTIME / "logs"
LOG_FILE = LOG_DIR / "boot.log"


def log(message: str) -> None:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    line = f"[RootRecord Ops] {message}"
    print(line, flush=True)
    with LOG_FILE.open("a", encoding="utf-8") as handle:
        handle.write(line + "\n")


def run(command: list[str]) -> None:
    log("RUN " + " ".join(command))
    subprocess.run(command, cwd=ROOT, check=True)


def fingerprint(*paths: Path) -> str:
    digest = hashlib.sha256()
    for path in paths:
        if path.is_file():
            digest.update(str(path.relative_to(ROOT)).encode())
            digest.update(path.read_bytes())
    return digest.hexdigest()


def ensure_dependencies() -> None:
    if not shutil.which("git"):
        log("WARNING missing dependency: git")
    pyproject = ROOT / "pyproject.toml"
    if pyproject.is_file():
        python = ROOT / ".venv" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
        if not python.exists():
            log("Creating Python virtual environment")
            run([sys.executable, "-m", "venv", str(python.parent.parent)])
        marker = RUNTIME / "python-dependencies.sha256"
        current = fingerprint(pyproject)
        needs_install = not marker.exists() or marker.read_text().strip() != current
        if needs_install:
            run([str(python), "-m", "pip", "install", "--upgrade", "pip"])
            run([str(python), "-m", "pip", "install", "-e", str(ROOT)])
            marker.write_text(current, encoding="utf-8")
    package = ROOT / "package.json"
    lock = ROOT / "package-lock.json"
    if package.is_file() and shutil.which("npm"):
        marker = RUNTIME / "node-dependencies.sha256"
        current = fingerprint(package, lock)
        needs_install = not marker.exists() or marker.read_text().strip() != current
        if needs_install:
            run(["npm", "ci" if lock.is_file() else "install"])
            marker.write_text(current, encoding="utf-8")


def main() -> int:
    log("BOOT_START")
    for directory in (RUNTIME, RUNTIME / "data", RUNTIME / "config", RUNTIME / "logs", ROOT / "media"):
        directory.mkdir(parents=True, exist_ok=True)
        log(f"READY {directory.relative_to(ROOT)}")
    ensure_dependencies()
    log("BOOT_COMPLETE")
    log(f"Log file: {LOG_FILE}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
