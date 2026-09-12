#!/usr/bin/env python3
"""Generate the Android launcher icons from the shipped iOS app icon.

One source of truth for the mark: `AppIcon-1024.png`. Rather than hand-cut a
second set of art, this splits that icon into the two layers an adaptive icon
needs -- a full-bleed gradient background and a transparent foreground holding
the mark itself -- so the launcher's mask crops padding instead of the design.

The split is by hue, not by a flat colour key, because the ground is a gradient
and no single RGB value describes it. White (low saturation) and the accent
dots all fall outside the purple band and survive; the ground does not. The
purple inside the rounded square becomes transparent too, which is correct: the
background layer shows through exactly where it did before.
"""

from __future__ import annotations

import colorsys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "EZTriviaApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
RES = ROOT / "android/app/src/main/res"

# Sampled from the source corners.
GROUND_START = (45, 35, 145)
GROUND_END = (98, 45, 180)

# Hue band, in degrees, that counts as ground rather than mark.
GROUND_HUE = (235, 285)
GROUND_MIN_SATURATION = 0.40

# An adaptive icon is a 108dp canvas of which only the central 72dp is
# guaranteed visible; everything outside can be masked away.
CANVAS = 108
SAFE = 72

# dp -> px multipliers for the density buckets Android expects.
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def extract_mark(source: Image.Image) -> Image.Image:
    """The icon with its purple ground removed, trimmed to the art."""
    source = source.convert("RGB")
    width, height = source.size
    pixels = source.load()
    mark = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    target = mark.load()

    for y in range(height):
        for x in range(width):
            red, green, blue = pixels[x, y]
            hue, saturation, _ = colorsys.rgb_to_hsv(red / 255, green / 255, blue / 255)
            degrees = hue * 360
            is_ground = (
                GROUND_HUE[0] <= degrees <= GROUND_HUE[1]
                and saturation >= GROUND_MIN_SATURATION
            )
            if not is_ground:
                target[x, y] = (red, green, blue, 255)

    box = mark.getbbox()
    if box is None:
        raise SystemExit("the hue split removed every pixel; check GROUND_HUE")
    return mark.crop(box)


def ground(size: int) -> Image.Image:
    """The background gradient, redrawn at an arbitrary size."""
    image = Image.new("RGB", (size, size))
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / max(2 * size - 2, 1)
            pixels[x, y] = tuple(
                int(GROUND_START[i] + (GROUND_END[i] - GROUND_START[i]) * t)
                for i in range(3)
            )
    return image


def centered(mark: Image.Image, canvas_px: int, content_px: int) -> Image.Image:
    """`mark` scaled to fit `content_px`, centered on a transparent canvas."""
    scaled = mark.copy()
    scaled.thumbnail((content_px, content_px), Image.LANCZOS)
    out = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    out.paste(
        scaled,
        ((canvas_px - scaled.width) // 2, (canvas_px - scaled.height) // 2),
        scaled,
    )
    return out


def main() -> int:
    if not SOURCE.exists():
        raise SystemExit(f"missing source icon: {SOURCE}")
    mark = extract_mark(Image.open(SOURCE))
    print(f"mark extracted: {mark.size[0]}x{mark.size[1]}")

    for bucket, scale in DENSITIES.items():
        directory = RES / f"mipmap-{bucket}"
        directory.mkdir(parents=True, exist_ok=True)

        canvas_px = int(CANVAS * scale)
        content_px = int(SAFE * scale)
        centered(mark, canvas_px, content_px).save(directory / "ic_launcher_foreground.png")

        # Legacy square and round icons for launchers that ignore the adaptive
        # pair. 48dp is the legacy launcher size.
        legacy_px = int(48 * scale)
        composed = ground(legacy_px).convert("RGBA")
        composed.alpha_composite(centered(mark, legacy_px, int(legacy_px * 0.86)))
        composed.convert("RGB").save(directory / "ic_launcher.png")

        circular = composed.copy()
        mask = Image.new("L", (legacy_px, legacy_px), 0)
        from PIL import ImageDraw

        ImageDraw.Draw(mask).ellipse((0, 0, legacy_px - 1, legacy_px - 1), fill=255)
        circular.putalpha(mask)
        circular.save(directory / "ic_launcher_round.png")

        print(f"  mipmap-{bucket}: foreground {canvas_px}px, legacy {legacy_px}px")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
