"""Copy load.jpg into drawable-nodpi/splash_image.png for app loading screen."""
from pathlib import Path
import os

from PIL import Image

ROOT = Path(os.environ.get("ROOTMC_ANDROID_ROOT") or "/home/rootrecord/.ollama/skills/origin/apps/rootmc-android")
SRC = ROOT / "load.jpg"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "splash_image.png"


def main() -> None:
    if not SRC.is_file():
        raise SystemExit(f"Missing {SRC}")
    Image.open(SRC).convert("RGB").save(OUT, format="PNG", optimize=True)
    print(f"Wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
