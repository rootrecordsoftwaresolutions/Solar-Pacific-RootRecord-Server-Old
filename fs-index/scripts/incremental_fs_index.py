#!/usr/bin/env python3
"""Incremental full-machine path index. Re-walks only directories whose mtime changed."""
from __future__ import annotations

import json
import os
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

HST = ZoneInfo("Pacific/Honolulu")
SKILL = Path(__file__).resolve().parents[1]
STATE = SKILL / "state"
PATHS_FILE = SKILL / "paths.txt"
MTIME_FILE = STATE / "dir-mtimes.json"
CURRENT = SKILL / "CURRENT.md"

# Descend these. Everything else under / is recorded as a single entry.
WALK_ROOTS = (
    Path.home(),
    Path("/mnt"),
    Path("/media"),
    Path("/opt"),
    Path("/usr"),
    Path("/etc"),
    Path("/var"),
    Path("/srv"),
    Path("/root"),
)

# List the directory, do not open children (blob / overlay / kernel fs).
NO_DESCEND_NAMES = frozenset(
    {
        "proc",
        "sys",
        "dev",
        "run",
        "snap",
        "objects",  # .git/objects
        "node_modules",
        ".venv",
        "venv",
        "__pycache__",
        "overlay2",
        "containers",
        "docker",
        "SteamLibrary",
        ".Trash-1000",
        "lost+found",
    }
)
NO_DESCEND_ROOTS = frozenset(
    {
        "/proc",
        "/sys",
        "/dev",
        "/run",
        "/snap",
        "/boot",
    }
)


def now_iso() -> str:
    return datetime.now(HST).isoformat(timespec="seconds")


def no_descend(path: Path) -> bool:
    posix = path.as_posix()
    if posix in NO_DESCEND_ROOTS:
        return True
    if path.name in NO_DESCEND_NAMES:
        return True
    if posix.startswith("/usr/src") or posix.startswith("/usr/include"):
        return True
    if posix in {"/var/cache", "/var/tmp", "/var/lib/apt", "/var/lib/dpkg"}:
        return True
    if "android-sdk" in path.parts and path.name in {"data", "lib", "lib64", ".temp"}:
        return True
    return False


def load_mtimes() -> dict[str, float]:
    if not MTIME_FILE.is_file():
        return {}
    try:
        raw = json.loads(MTIME_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return raw if isinstance(raw, dict) else {}


def load_old_paths() -> list[str]:
    if not PATHS_FILE.is_file():
        return []
    try:
        return PATHS_FILE.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []


def slice_prefix(old: list[str], prefix: str) -> list[str]:
    """Paths equal to prefix or nested under prefix/."""
    hit = []
    pfx = prefix if prefix.endswith("/") else prefix + "/"
    for line in old:
        base = line.split("\t", 1)[0]
        if base == prefix or base.startswith(pfx):
            hit.append(line)
    return hit


def fmt_entry(path: Path, *, kind: str, extra: str = "") -> str:
    posix = path.as_posix()
    if extra:
        return f"{posix}\t{kind}\t{extra}"
    return f"{posix}\t{kind}"


def walk_tree(
    root: Path,
    *,
    old_paths: list[str],
    mtimes: dict[str, float],
    seen_dev_ino: set[tuple[int, int]],
    stats: dict,
) -> list[str]:
    out: list[str] = []
    if not root.exists():
        return [fmt_entry(root, kind="missing")]

    def rec(cur: Path) -> None:
        stats["visited"] += 1
        try:
            st = cur.lstat()
        except OSError as e:
            out.append(fmt_entry(cur, kind="error", extra=str(e)[:80]))
            stats["errors"] += 1
            return
        key = (st.st_dev, st.st_ino)
        if key in seen_dev_ino:
            out.append(fmt_entry(cur, kind="cycle"))
            return
        seen_dev_ino.add(key)
        posix = cur.as_posix()

        if os.path.islink(cur):
            target = ""
            try:
                target = os.readlink(cur)
            except OSError:
                target = "?"
            out.append(fmt_entry(cur, kind="symlink", extra=target[:200]))
            stats["symlinks"] += 1
            return

        if not cur.is_dir():
            out.append(fmt_entry(cur, kind="file"))
            stats["files"] += 1
            return

        if no_descend(cur):
            out.append(fmt_entry(cur, kind="dir-stub"))
            stats["stubs"] += 1
            mtimes[posix] = st.st_mtime
            return

        prev_m = mtimes.get(posix)
        if (
            prev_m is not None
            and prev_m == st.st_mtime
            and old_paths
        ):
            reused = slice_prefix(old_paths, posix)
            if reused:
                out.extend(reused)
                stats["reused_dirs"] += 1
                stats["reused_lines"] += len(reused)
                return

        stats["rewalked_dirs"] += 1
        out.append(fmt_entry(cur, kind="dir"))
        mtimes[posix] = st.st_mtime
        try:
            with os.scandir(cur) as it:
                kids = sorted(it, key=lambda e: e.name.lower())
        except OSError as e:
            out.append(fmt_entry(cur, kind="unreadable", extra=str(e)[:80]))
            stats["errors"] += 1
            return
        for ent in kids:
            rec(Path(ent.path))

    rec(root)
    return out


def merge_roots(parts: list[list[str]]) -> list[str]:
    seen: set[str] = set()
    merged: list[str] = []
    for block in parts:
        for line in block:
            key = line.split("\t", 1)[0]
            if key in seen:
                continue
            seen.add(key)
            merged.append(line)
    merged.sort(key=lambda s: s.split("\t", 1)[0].lower())
    return merged


def write_current(stats: dict, n_paths: int) -> None:
    lines = [
        "# Live machine index — run stats",
        "",
        f"Generated {now_iso()}.",
        "",
        f"- paths indexed: **{n_paths}**",
        f"- dir re-walks: {stats.get('rewalked_dirs', 0)}",
        f"- dirs reused (mtime match): {stats.get('reused_dirs', 0)}",
        f"- lines reused: {stats.get('reused_lines', 0)}",
        f"- files seen this walk: {stats.get('files', 0)}",
        f"- stubs (not descended): {stats.get('stubs', 0)}",
        f"- symlinks: {stats.get('symlinks', 0)}",
        f"- errors: {stats.get('errors', 0)}",
        "",
        "Full map: `paths.txt`. Columns: `path`, `kind`, optional extra (symlink target).",
        "",
        "## Walk roots",
        "",
    ]
    for p in WALK_ROOTS:
        lines.append(f"- `{p}` {'ok' if p.exists() else 'missing'}")
    lines += [
        "",
        "## / (top, not fully descended except walk roots)",
        "",
    ]
    try:
        for ent in sorted(os.scandir("/"), key=lambda e: e.name.lower()):
            mark = "walk" if Path(ent.path) in {Path(str(r)) for r in WALK_ROOTS} else "top"
            kind = "dir" if ent.is_dir(follow_symlinks=False) else "file"
            lines.append(f"- `/{ent.name}` — {kind} ({mark})")
    except OSError as e:
        lines.append(f"- unreadable / : {e}")
    lines.append("")
    CURRENT.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    STATE.mkdir(parents=True, exist_ok=True)
    old_paths = load_old_paths()
    mtimes = load_mtimes()
    stats = {
        "visited": 0,
        "rewalked_dirs": 0,
        "reused_dirs": 0,
        "reused_lines": 0,
        "files": 0,
        "stubs": 0,
        "symlinks": 0,
        "errors": 0,
    }
    seen: set[tuple[int, int]] = set()
    blocks: list[list[str]] = []

    # Always record / itself
    blocks.append([fmt_entry(Path("/"), kind="dir")])
    try:
        with os.scandir("/") as it:
            for ent in sorted(it, key=lambda e: e.name.lower()):
                p = Path(ent.path)
                if p in WALK_ROOTS:
                    continue
                if no_descend(p) or p.as_posix() in NO_DESCEND_ROOTS:
                    blocks.append([fmt_entry(p, kind="dir-stub")])
                    stats["stubs"] += 1
                    continue
                if ent.is_symlink():
                    blocks.append(
                        [fmt_entry(p, kind="symlink", extra=os.readlink(p)[:200])]
                    )
                    stats["symlinks"] += 1
                    continue
                if ent.is_file(follow_symlinks=False):
                    blocks.append([fmt_entry(p, kind="file")])
                    stats["files"] += 1
                    continue
                blocks.append([fmt_entry(p, kind="dir-stub")])
                stats["stubs"] += 1
    except OSError as e:
        stats["errors"] += 1
        blocks.append([fmt_entry(Path("/"), kind="unreadable", extra=str(e)[:80])])

    for root in WALK_ROOTS:
        blocks.append(
            walk_tree(
                root,
                old_paths=old_paths,
                mtimes=mtimes,
                seen_dev_ino=seen,
                stats=stats,
            )
        )

    merged = merge_roots(blocks)
    tmp = PATHS_FILE.with_suffix(".txt.tmp")
    tmp.write_text("\n".join(merged) + ("\n" if merged else ""), encoding="utf-8")
    tmp.replace(PATHS_FILE)
    MTIME_FILE.write_text(json.dumps(mtimes, indent=0, sort_keys=True) + "\n", encoding="utf-8")
    write_current(stats, len(merged))
    print(
        f"paths={len(merged)} rewalk={stats['rewalked_dirs']} "
        f"reuse_dirs={stats['reused_dirs']} reuse_lines={stats['reused_lines']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
