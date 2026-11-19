#!/usr/bin/env python3
"""Build Play marketing assets from actual Star/Run game captures and its original logo.

Never paint a simulated screen or invent a card. The screenshots come directly
from StarRun's existing Godot screenshot_harness.gd render output.
"""
from __future__ import annotations

import json
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
CONFIG = json.loads((ROOT / "play-store-starrun/en-US.json").read_text())
GAME = Path(os.environ.get("STARRUN_SOURCE", "starrun"))
CAPTURES = Path(os.environ["STARRUN_SCREENSHOT_SOURCE"])
OUT = ROOT / "play-store-starrun/generated"
ICON = OUT / "icon.png"
FEATURE = OUT / "feature-graphic.png"
SCREENSHOTS = OUT / "screenshots"
NAVY = (7, 12, 25)
CYAN = (96, 244, 225)
CREAM = (240, 247, 255)
GOLD = (255, 192, 120)


def font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    paths = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf" if bold
        else "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    ]
    for candidate in paths:
        if Path(candidate).exists():
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def hero() -> None:
    width, height = 1024, 500
    image = Image.new("RGB", (width, height), NAVY)
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            blend = min(1.0, x / width * 0.8 + y / height * 0.2)
            pixels[x, y] = (
                round(7 + 9 * blend),
                round(12 + 17 * blend),
                round(25 + 34 * blend),
            )
    base = image.convert("RGBA")
    glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    drawer = ImageDraw.Draw(glow)
    drawer.ellipse((-70, 75, 440, 585), fill=(*CYAN, 75))
    drawer.ellipse((620, -230, 1200, 320), fill=(*GOLD, 28))
    base.alpha_composite(glow.filter(ImageFilter.GaussianBlur(83)))
    grid = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(grid)
    for x in range(30, width, 52):
        d.line((x, 0, x, height), fill=(105, 151, 185, 21), width=1)
    for y in range(0, height, 52):
        d.line((0, y, width, y), fill=(105, 151, 185, 21), width=1)
    base.alpha_composite(grid)
    mark = Image.open(ICON).convert("RGBA")
    mark.thumbnail((310, 310), Image.Resampling.LANCZOS)
    x, y = 68, 96
    shadow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    shadow_patch = Image.new("RGBA", mark.size, (0, 0, 0, 165))
    shadow_patch.putalpha(mark.getchannel("A").filter(ImageFilter.GaussianBlur(16)))
    shadow.alpha_composite(shadow_patch, (x + 12, y + 19))
    base.alpha_composite(shadow)
    base.alpha_composite(mark, (x, y))
    draw = ImageDraw.Draw(base)
    draw.text((426, 85), "BUILD THE SHIP", font=font(24, True), fill=CYAN)
    draw.text((421, 137), "Star/Run", font=font(82, True), fill=CREAM)
    draw.text((427, 257), "Your build. Your run.", font=font(34, True), fill=GOLD)
    draw.text((427, 337), "FOUR WORLDS  •  SIX SHIPS", font=font(21, True), fill=CREAM)
    draw.text((427, 369), "DAILY CHALLENGES  •  ENDLESS MODE", font=font(17), fill=(171, 194, 215))
    FEATURE.parent.mkdir(parents=True, exist_ok=True)
    base.convert("RGB").save(FEATURE, format="PNG", optimize=True)


def main() -> None:
    if CONFIG["packageName"] != "com.rsm.starrun":
        raise SystemExit("Refusing to generate another game's artwork")
    original = GAME / "assets/android-icon.png"
    if not original.is_file():
        raise SystemExit("The original Godot Android launcher art must be generated first")
    OUT.mkdir(parents=True, exist_ok=True)
    with Image.open(original) as mark:
        if mark.size != (512, 512):
            raise SystemExit(f"Godot original launcher icon must be 512x512, got {mark.size}")
        mark.convert("RGBA").save(ICON, format="PNG")
    SCREENSHOTS.mkdir(parents=True, exist_ok=True)
    chosen = CONFIG["screenshots"]
    if len(chosen) < 4 or len(chosen) > 8 or len(chosen) != len(set(chosen)):
        raise SystemExit("Choose 4-8 unique real gameplay screenshots")
    for index, basename in enumerate(chosen, 1):
        source = CAPTURES / "390x844" / (basename + ".png")
        if not source.is_file():
            raise SystemExit(f"Missing actual Godot screenshot {source}")
        with Image.open(source) as shot:
            if shot.size != (390, 844):
                raise SystemExit(f"Unexpected capture dimensions for {basename}: {shot.size}")
            output = SCREENSHOTS / f"{index:02d}-{basename}.png"
            shot.convert("RGB").resize((1170, 2532), Image.Resampling.LANCZOS).save(output, "PNG", optimize=True)
            print(f"Gameplay capture: {basename} -> {output.name} (1170x2532)")
    hero()
    with Image.open(FEATURE) as feature:
        if feature.size != (1024, 500):
            raise SystemExit("Play feature graphic has incorrect dimensions")
    print(f"Original icon: {ICON} (512x512)")
    print(f"Feature graphic: {FEATURE} (1024x500)")
    print(f"Generated {len(chosen)} real in-game phone screenshots")


if __name__ == "__main__":
    main()
