#!/usr/bin/env python3
"""Build a looping GIF from the last frame of each hourly weather snapshot."""
from __future__ import annotations

import argparse
import os
import sys
import tempfile
from pathlib import Path

from PIL import Image


def _use_dest_tmpdir(dest: Path) -> None:
    d = str(dest.parent)
    dest.parent.mkdir(parents=True, exist_ok=True)
    os.environ["TMPDIR"] = d
    os.environ["TEMP"] = d
    os.environ["TMP"] = d
    tempfile.tempdir = d


def last_frame(path: Path) -> Image.Image:
    im = Image.open(path)
    n = getattr(im, "n_frames", 1) or 1
    im.seek(max(0, n - 1))
    frame = im.convert("RGBA")
    im.close()
    return frame


def assemble(paths: list[Path], dest: Path, delay_ms: int) -> None:
    frames: list[Image.Image] = []
    for p in paths:
        try:
            frames.append(last_frame(p))
        except Exception as err:  # noqa: BLE001 — skip a bad hourly file, keep the loop
            print(f"skip {p.name}: {err}", file=sys.stderr)
    if len(frames) < 2:
        raise SystemExit("need at least 2 readable frames")
    w, h = frames[0].size
    sized: list[Image.Image] = []
    for fr in frames:
        if fr.size != (w, h):
            fr = fr.resize((w, h), Image.Resampling.LANCZOS)
        sized.append(fr.convert("P", palette=Image.Palette.ADAPTIVE, colors=256))
    _use_dest_tmpdir(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = Path(str(dest) + ".tmp")
    sized[0].save(
        tmp,
        format="GIF",
        save_all=True,
        append_images=sized[1:],
        duration=max(80, delay_ms),
        loop=0,
        disposal=2,
        optimize=False,
    )
    tmp.replace(dest)


def extract_last(src: Path, dest: Path) -> None:
    frame = last_frame(src)
    _use_dest_tmpdir(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = Path(str(dest) + ".tmp")
    frame.convert("P", palette=Image.Palette.ADAPTIVE, colors=256).save(tmp, format="GIF")
    tmp.replace(dest)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--extract-last", nargs=2, metavar=("IN", "OUT"))
    ap.add_argument("--out")
    ap.add_argument("--delay-ms", type=int, default=350)
    ap.add_argument("inputs", nargs="*")
    args = ap.parse_args()
    if args.extract_last:
        extract_last(Path(args.extract_last[0]), Path(args.extract_last[1]))
        return
    if not args.out or len(args.inputs) < 2:
        raise SystemExit("need --out and at least 2 inputs, or --extract-last IN OUT")
    assemble([Path(p) for p in args.inputs], Path(args.out), args.delay_ms)


if __name__ == "__main__":
    main()
