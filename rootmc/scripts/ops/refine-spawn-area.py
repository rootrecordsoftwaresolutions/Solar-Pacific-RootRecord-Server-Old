#!/usr/bin/env python3
"""Copy spawnarea.txt waypoints to spawnarea-refined.txt (wall polygon — no circular smooth)."""
from __future__ import annotations

import argparse
from pathlib import Path


def parse_rows(text: str) -> list[tuple[str, int, int]]:
    rows: list[tuple[str, int, int]] = []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or line.startswith("session_") or line.startswith("world="):
            continue
        if line.startswith("---") or line.startswith("updated_at"):
            continue
        parts = [p.strip() for p in line.split(",")]
        if len(parts) < 4:
            continue
        try:
            rows.append((parts[1], int(parts[2]), int(parts[3])))
        except ValueError:
            continue
    return rows


def dedupe_ring(points: list[tuple[int, int]]) -> list[tuple[int, int]]:
    if not points:
        return points
    out = [points[0]]
    for p in points[1:]:
        if p != out[-1]:
            out.append(p)
    if len(out) > 1 and out[0] == out[-1]:
        out.pop()
    return out


def main() -> None:
    root = Path("/home/rootrecord/.ollama/skills/origin/workstations/minecraft-plugins/plugins/root-spawn")
    parser = argparse.ArgumentParser(description="Import spawnarea.txt → spawnarea-refined.txt")
    parser.add_argument(
        "--input",
        type=Path,
        default=root / "spawnarea.txt",
        help="Waypoint file (default: plugin spawnarea.txt)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=root / "spawnarea-refined.txt",
        help="Refined boundary output",
    )
    args = parser.parse_args()

    rows = parse_rows(args.input.read_text(encoding="utf-8"))
    if len(rows) < 3:
        raise SystemExit(f"Need at least 3 waypoints in {args.input}")

    world = rows[0][0]
    ring = dedupe_ring([(x, z) for _, x, z in rows])

    lines = [
        "# RootMC spawn safe zone perimeter",
        "# Format: index,world,x,z",
        "# Wall polygon — imported from spawnarea.txt (no circular smooth)",
        f"world={world}",
        "---",
    ]
    for i, (x, z) in enumerate(ring, start=1):
        lines.append(f"{i},{world},{x},{z}")

    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(ring)} points to {args.output}")


if __name__ == "__main__":
    main()
