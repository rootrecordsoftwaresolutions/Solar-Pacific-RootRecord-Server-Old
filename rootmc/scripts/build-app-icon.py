"""Build 512px Play Store icon + Android mipmap launcher PNGs from source artwork."""
from __future__ import annotations

import os
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(os.environ.get("ROOTMC_ANDROID_ROOT") or "/home/rootrecord/.ollama/skills/origin/apps/rootmc-android")
SRC = ROOT / "Screenshot 2026-06-03 194128.png"
OUT_PLAY = ROOT / "store-assets" / "play-store-icon-512.png"

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Pixels at or below this RGB sum become transparent (removes screenshot letterboxing).
BLACK_THRESHOLD = 28


def remove_letterbox_black(img: Image.Image) -> Image.Image:
    rgba = img.convert("RGBA")
    px = rgba.load()
    w, h = rgba.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if r + g + b <= BLACK_THRESHOLD:
                px[x, y] = (r, g, b, 0)
    return rgba


def crop_to_content(img: Image.Image) -> Image.Image:
    bbox = img.getbbox()
    if not bbox:
        return img
    return img.crop(bbox)


def fit_square(img: Image.Image, size: int, *, fill_canvas: bool = False) -> Image.Image:
    """Center on size×size canvas. fill_canvas scales up to cover (Play Store icon)."""
    img = crop_to_content(img)
    if img.width == 0 or img.height == 0:
        return Image.new("RGBA", (size, size), (0, 0, 0, 0))

    if fill_canvas:
        scale = max(size / img.width, size / img.height)
        nw = max(1, int(img.width * scale))
        nh = max(1, int(img.height * scale))
        img = img.resize((nw, nh), Image.Resampling.LANCZOS)
        left = (nw - size) // 2
        top = (nh - size) // 2
        return img.crop((left, top, left + size, top + size))

    img = img.copy()
    img.thumbnail((size, size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ox = (size - img.width) // 2
    oy = (size - img.height) // 2
    canvas.paste(img, (ox, oy), img)
    return canvas


def main() -> int:
    if not SRC.is_file():
        print(f"Missing source image: {SRC}", file=sys.stderr)
        return 1

    raw = Image.open(SRC)
    step1 = remove_letterbox_black(raw)
    step2 = crop_to_content(step1)
    icon512 = fit_square(step2, 512, fill_canvas=True)

    OUT_PLAY.parent.mkdir(parents=True, exist_ok=True)
    icon512.save(OUT_PLAY, format="PNG", optimize=True)
    print(f"Wrote {OUT_PLAY} ({OUT_PLAY.stat().st_size} bytes)")

    res = ROOT / "app" / "src" / "main" / "res"
    for folder, px in MIPMAP_SIZES.items():
        out_dir = res / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        sized = fit_square(step2, px, fill_canvas=True)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            path = out_dir / name
            sized.save(path, format="PNG", optimize=True)
        print(f"Wrote {folder} ({px}px)")

    # Adaptive icon foreground (108dp @ xxxhdpi = 432px; use 512 scaled down)
    fg_dir = res / "drawable-nodpi"
    fg_dir.mkdir(parents=True, exist_ok=True)
    fg = fit_square(step2, 432, fill_canvas=True)
    fg_path = fg_dir / "ic_launcher_foreground.png"
    fg.save(fg_path, format="PNG", optimize=True)
    print(f"Wrote {fg_path} (432px)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
