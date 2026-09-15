#!/usr/bin/env python3
"""Generate the Google Play 512x512 store icon from the shipped app icon."""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "EZTriviaApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
OUTPUT = ROOT / "play-store-assets/icon.png"


def main() -> int:
    if not SOURCE.is_file():
        raise SystemExit(f"Missing source app icon: {SOURCE}")

    image = Image.open(SOURCE)
    if image.size != (1024, 1024):
        raise SystemExit(f"Expected 1024x1024 source icon, got {image.size[0]}x{image.size[1]}")

    # Google Play accepts a 512x512 PNG. Use an opaque RGB output so the store
    # icon is deterministic and cannot accidentally pick up an alpha channel.
    icon = image.convert("RGB").resize((512, 512), Image.Resampling.LANCZOS)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    icon.save(OUTPUT, format="PNG", optimize=True)

    check = Image.open(OUTPUT)
    if check.size != (512, 512) or check.mode != "RGB":
        raise SystemExit(
            f"Generated icon failed validation: size={check.size}, mode={check.mode}"
        )

    print(f"Generated {OUTPUT.relative_to(ROOT)}: 512x512 RGB PNG")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
