"""Push safe website source changes to the Ava Ivy Cloud GitHub repository."""

from __future__ import annotations

import os
import subprocess
import sys
from datetime import datetime
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
GIT = Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Git" / "cmd" / "git.exe"
if not GIT.is_file():
    GIT = Path("git")

SAFE_PATHS = (
    "src", "public", "app", "app/globals.css", "app/layout.tsx", "app/page.tsx", "package.json", "package-lock.json", "next.config.ts",
    "next-env.d.ts", "tsconfig.json", "vercel.json", "README.md", "AGENTS.md",
    "scripts/auto-push.py",
)
SECRET_NAMES = {".env", ".env.local", "credentials.env", "origin.token"}


def git(*args: str) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["GIT_TERMINAL_PROMPT"] = "0"
    env["GCM_INTERACTIVE"] = "never"
    return subprocess.run([str(GIT), *args], cwd=REPO, env=env, text=True, capture_output=True, check=False)


def main() -> int:
    if os.environ.get("VERCEL_AUTO_PUSH", "1").lower() in {"0", "false", "no"}:
        return 0
    if not (REPO / ".git").exists():
        return 0
    git("add", "-A", "--", *SAFE_PATHS)
    existing = [path for path in SAFE_PATHS if (REPO / path).exists()]
    if existing:
        git("add", "--", *existing)
    staged = git("diff", "--cached", "--name-only").stdout.splitlines()
    secrets = [path for path in staged if Path(path).name.lower() in SECRET_NAMES]
    if secrets:
        git("restore", "--staged", "--", *secrets)
    if git("diff", "--cached", "--quiet").returncode == 0:
        return 0
    stamp = datetime.now().astimezone().strftime("%Y-%m-%d %H:%M %Z")
    commit = git("commit", "-m", f"auto: sync website {stamp}")
    if commit.returncode != 0:
        sys.stderr.write(commit.stderr or commit.stdout)
        return commit.returncode or 1
    push = git("push", "origin", "HEAD:main")
    if push.returncode != 0:
        sys.stderr.write(push.stderr or push.stdout)
        return push.returncode or 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())