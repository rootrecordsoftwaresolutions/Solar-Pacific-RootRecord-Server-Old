#!/usr/bin/env python3
"""Copy unique docs from named Archives trees into ecosystem-history. No secrets. No LTC. No DB dumps."""
from __future__ import annotations

import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

SKILLS = Path.home() / ".ollama" / "skills"
DEST = SKILLS / "ecosystem-history" / "references" / "archives-pull-20260916"
EXISTING_DOCS = SKILLS / "ecosystem-history" / "references" / "ava-docs"
EXISTING_NAMES = {p.name for p in EXISTING_DOCS.rglob("*") if p.is_file()} if EXISTING_DOCS.is_dir() else set()

SKIP_DIR_PARTS = {
    ".credentials",
    "secrets",
    "gemini_env",
    "litecoin",
    ".litecoin",
    "steamlibrary",
    "ollama-models",
    "all extracted media",
    "node_modules",
    ".git",
    "$recycle.bin",
    "ebooks",
    "worklogs",
    "_archive",
}
SKIP_NAMES = {
    "credentials.env",
    ".env",
    "solana.json",
    "ava.zip",
}
SKIP_SUFFIX = {".db", ".sqlite", ".sqlite3", ".zip", ".7z", ".mp4", ".mp3", ".wav", ".png", ".jpg", ".webp", ".ogg"}
KEEP_SUFFIX = {".md", ".txt", ".example"}
MAX_BYTES = 2 * 1024 * 1024


def _skip_dir(path: Path) -> bool:
    parts = {p.lower() for p in path.parts}
    if parts & SKIP_DIR_PARTS:
        return True
    name = path.name.lower()
    if name.startswith(".env"):
        return True
    return False


def _want_file(path: Path) -> bool:
    if not path.is_file():
        return False
    name = path.name.lower()
    if name in SKIP_NAMES or name.startswith(".env"):
        return False
    if name.startswith("db") and len(name) >= 40 and "." not in name:
        return False
    if path.suffix.lower() in SKIP_SUFFIX:
        return False
    if name.endswith(".env") and name != "credentials.env.example":
        return False
    if path.suffix.lower() not in KEEP_SUFFIX and name not in {"readme", "license"}:
        if not (name.endswith(".md") or name.endswith(".txt") or name.endswith(".example")):
            return False
    try:
        if path.stat().st_size > MAX_BYTES:
            return False
    except OSError:
        return False
    return True


def _rel_safe(src: Path, root: Path) -> Path:
    try:
        return src.relative_to(root)
    except ValueError:
        return Path(src.name)


def copy_tree(root: Path, label: str, *, maxdepth: int = 8) -> list[str]:
    copied: list[str] = []
    if not root.exists():
        return copied
    dest_root = DEST / label
    for path in root.rglob("*"):
        if _skip_dir(path):
            continue
        try:
            depth = len(path.relative_to(root).parts)
        except ValueError:
            continue
        if depth > maxdepth:
            continue
        if path.is_dir():
            continue
        if not _want_file(path):
            continue
        rel = _rel_safe(path, root)
        if path.name in EXISTING_NAMES:
            continue
        dest = dest_root / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.is_file():
            continue
        shutil.copy2(path, dest)
        copied.append(str(rel))
    return copied


def copy_named(src: Path, dest_rel: str) -> bool:
    if not src.is_file() or not _want_file(src):
        return False
    dest = DEST / dest_rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dest)
    return True


def main() -> None:
    DEST.mkdir(parents=True, exist_ok=True)
    report: dict = {"at": datetime.now(timezone.utc).isoformat(), "copied": {}, "skipped_note": "no secrets, no LTC, no db dumps, no media"}

    git_cfg = Path("/mnt/Archives/.git/config")
    if git_cfg.is_file():
        text = git_cfg.read_text(encoding="utf-8", errors="replace")
        (DEST / "git-monorepo-remote.txt").write_text(
            "Archives orphan .git (399M objects discarded).\n"
            "Remote was https://github.com/Rootmcnet/MonoRepo.git\n\n"
            + text,
            encoding="utf-8",
        )
        report["copied"]["git-note"] = 1

    roots = {
        "pre-august-top": Path("/mnt/Archives/Pre August"),
        "pre-august-doc-repo": Path("/mnt/Archives/Pre August/Doc-Repo/docs"),
        "pre-august-cloudflare-md": Path("/mnt/Archives/Pre August/Cloudflare"),
        "pre-august-marketing": Path("/mnt/Archives/Pre August/.1 Work Stations/Minecraft-Marketing"),
        "pre-august-rootmc-docs": Path("/mnt/Archives/Pre August/.1 Work Stations/RootMC"),
        "db-backup-maps": Path("/mnt/Archives/db backup"),
        "september-topics": Path("/mnt/Archives/September/Topics"),
        "september-ava-docs": Path("/mnt/Archives/September/09092026/ava/docs"),
        "august-emergency-txt": Path("/mnt/Archives/August 2026/Emergency pack"),
    }
    for label, root in roots.items():
        if label == "db-backup-maps":
            n = 0
            for name in (
                "README.md",
                "Database_tree.txt",
                "file_mapping.txt",
                "unicode.md",
                "AVA BACKGROUND PROCESS.txt",
                "ecoflow_tree.txt",
                "logs.txt",
            ):
                if copy_named(root / name, f"db-backup-maps/{name}"):
                    n += 1
            data = root / "Database"
            for name in ("data_tree.txt", "Database.txt", "data.txt"):
                if copy_named(data / name, f"db-backup-maps/Database/{name}"):
                    n += 1
            report["copied"][label] = n
            continue
        if label == "pre-august-top":
            n = 0
            for p in root.iterdir():
                if p.is_file() and _want_file(p):
                    if copy_named(p, f"pre-august-top/{p.name}"):
                        n += 1
            ex = root / "credentials.env.example"
            if copy_named(ex, "pre-august-top/credentials.env.example"):
                n += 1
            report["copied"][label] = n
            continue
        if label == "pre-august-cloudflare-md":
            rows = []
            if root.is_dir():
                for p in root.rglob("*"):
                    if _skip_dir(p) or not _want_file(p):
                        continue
                    if p.suffix.lower() != ".md":
                        continue
                    rel = p.relative_to(root)
                    dest = DEST / label / rel
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(p, dest)
                    rows.append(str(rel))
            report["copied"][label] = len(rows)
            continue
        if label == "pre-august-rootmc-docs":
            # Change Logs + docs only
            n = 0
            for sub in ("Change Logs", "docs", "Marketing"):
                n += len(copy_tree(root / sub, f"{label}/{sub}", maxdepth=4))
            for name in ("WORKSPACE.md", "GEN-1-GEN-2.md", "Current history.txt"):
                if copy_named(root / name, f"{label}/{name}"):
                    n += 1
            report["copied"][label] = n
            continue
        if label == "august-emergency-txt":
            rows = copy_tree(root, label, maxdepth=3)
            report["copied"][label] = len(rows)
            continue
        rows = copy_tree(root, label, maxdepth=6)
        report["copied"][label] = len(rows)

    (DEST / "MANIFEST.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    lines = [
        "# Archives pull 2026-09-16",
        "",
        "Docs only. No Litecoin. No credentials.env. No sqlite dumps. No ava.zip. No extracted media.",
        "Source folders on `/mnt/Archives` were then deleted (allowlist).",
        "",
        "Counts:",
    ]
    for k, v in sorted(report["copied"].items()):
        lines.append(f"- {k}: {v}")
    (DEST / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps(report["copied"], indent=2))
    print("dest", DEST)


if __name__ == "__main__":
    main()
