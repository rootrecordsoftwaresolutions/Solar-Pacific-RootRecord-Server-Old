"""Play Store feature graphic: 1024×500 from graphic.jpg (center crop, no stretch)."""
from pathlib import Path
import os

from PIL import Image

ROOT = Path(os.environ.get("ROOTMC_ANDROID_ROOT") or "/home/rootrecord/.ollama/skills/origin/apps/rootmc-android")
SRC = ROOT / "graphic.jpg"
OUT_DIR = ROOT / "store-assets"
TARGET_W, TARGET_H = 1024, 500


def crop_and_resize(img: Image.Image) -> Image.Image:
    w, h = img.size
    target_ratio = TARGET_W / TARGET_H
    src_ratio = w / h
    if src_ratio > target_ratio:
        new_w = int(h * target_ratio)
        left = (w - new_w) // 2
        cropped = img.crop((left, 0, left + new_w, h))
    else:
        new_h = int(w / target_ratio)
        top = (h - new_h) // 2
        cropped = img.crop((0, top, w, top + new_h))
    return cropped.resize((TARGET_W, TARGET_H), Image.Resampling.LANCZOS)


def main() -> None:
    if not SRC.is_file():
        raise SystemExit(f"Missing {SRC}")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    img = Image.open(SRC).convert("RGB")
    final = crop_and_resize(img)
    jpg = OUT_DIR / "play-feature-graphic-1024x500.jpg"
    png = OUT_DIR / "play-feature-graphic-1024x500.png"
    final.save(jpg, format="JPEG", quality=92, optimize=True)
    final.save(png, format="PNG", optimize=True)
    print(f"Wrote {jpg} ({jpg.stat().st_size} bytes)")
    print(f"Wrote {png} ({png.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
