"""Parse <<<FILE>>> markers and zip for sendDocument."""
from __future__ import annotations

import re
import zipfile
from pathlib import Path

FILE_RE = re.compile(
    r'<<<FILE\s+path="([^"]+)"\s*>>>\s*(.*?)\s*<<<END_FILE>>>',
    re.DOTALL | re.IGNORECASE,
)


def parse_files(text: str) -> list[tuple[str, str]]:
    found: list[tuple[str, str]] = []
    for m in FILE_RE.finditer(text or ""):
        rel = m.group(1).strip().lstrip("/")
        if ".." in rel.split("/"):
            continue
        body = m.group(2)
        if rel:
            found.append((rel, body))
    return found


def strip_file_markers(text: str) -> str:
    cleaned = FILE_RE.sub("", text or "")
    return re.sub(r"\n{3,}", "\n\n", cleaned).strip()


def collect_and_zip(
    chunks: list[str],
    run_dir: Path,
    zip_name: str = "bundle.zip",
) -> Path | None:
    files: list[tuple[str, str]] = []
    for chunk in chunks:
        files.extend(parse_files(chunk))
    if not files:
        return None
    run_dir.mkdir(parents=True, exist_ok=True)
    files_dir = run_dir / "files"
    files_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for rel, body in files:
        dest = files_dir / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(body, encoding="utf-8")
        written.append(dest)
    zpath = run_dir / zip_name
    with zipfile.ZipFile(zpath, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for dest in written:
            arc = dest.relative_to(files_dir).as_posix()
            zf.write(dest, arcname=arc)
    if zpath.stat().st_size > 8 * 1024 * 1024:
        zpath.unlink(missing_ok=True)
        return None
    return zpath
