"""
Launch PaperMC from this folder. Picks the newest paper-*.jar if several exist.
"""
from __future__ import annotations

import glob
import os
import subprocess
import sys
from pathlib import Path


def find_paper_jar(folder: Path) -> Path | None:
    jars = sorted(folder.glob("paper-*.jar"), key=lambda p: p.stat().st_mtime, reverse=True)
    if jars:
        return jars[0]
    direct = folder / "paper.jar"
    if direct.is_file():
        return direct
    return None


def main() -> int:
    root = Path("/home/rootrecord/.ollama/skills/origin/workstations/minecraft-plugins/server")
    os.chdir(root)

    xms = os.environ.get("PAPER_XMS", "2G")
    xmx = os.environ.get("PAPER_XMX", "4G")
    java = os.environ.get("JAVA_HOME")
    java_exe = Path(java) / "bin" / "java.exe" if java else None
    if java_exe and java_exe.is_file():
        java_cmd = str(java_exe)
    else:
        java_cmd = "java"

    jar = find_paper_jar(root)
    if not jar:
        print("No paper-*.jar or paper.jar found in:", root, file=sys.stderr)
        return 1

    jvm_opts = os.environ.get("PAPER_JVM_OPTS", "")
    extra = jvm_opts.split() if jvm_opts.strip() else []

    cmd = [
        java_cmd,
        "-server",
        f"-Xms{xms}",
        f"-Xmx{xmx}",
        *extra,
        "-jar",
        str(jar),
        "--nogui",
    ]

    print("Starting PaperMC:", jar.name)
    print(" ".join(cmd[:6]), "...", "--nogui")
    try:
        return subprocess.call(cmd)
    except FileNotFoundError:
        print(
            "Java not found. Install a JDK that matches your Paper build, "
            "or set JAVA_HOME to that JDK (see https://docs.papermc.io/paper/getting-started/).",
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
