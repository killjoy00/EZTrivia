#!/usr/bin/env python3
"""Build the Google Play 1024x500 feature graphic from EZ Trivia's shipped icon."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "EZTriviaApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
OUTPUT = ROOT / "play-store-assets/feature-graphic.png"
WIDTH = 1024
HEIGHT = 500

# Keep the Play art tied to the same purple family used by the launcher icon.
GROUND_START = (45, 35, 145)
GROUND_END = (98, 45, 180)
WHITE = (255, 255, 255)
SOFT_WHITE = (232, 229, 255)
GOLD = (255, 198, 64)
CYAN = (78, 206, 255)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    ]
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            return ImageFont.truetype(str(path), size=size)
    return ImageFont.load_default()


def gradient() -> Image.Image:
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()
    for y in range(HEIGHT):
        for x in range(WIDTH):
            # Diagonal gradient that matches the Android launcher background.
            t = (x / (WIDTH - 1) * 0.72) + (y / (HEIGHT - 1) * 0.28)
            pixels[x, y] = tuple(
                int(GROUND_START[i] + (GROUND_END[i] - GROUND_START[i]) * t)
                for i in range(3)
            )
    return image.convert("RGBA")


def add_glow(canvas: Image.Image, box: tuple[int, int, int, int], color: tuple[int, int, int], alpha: int) -> None:
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.ellipse(box, fill=(*color, alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(58))
    canvas.alpha_composite(layer)


def fit_text(draw: ImageDraw.ImageDraw, text: str, max_width: int, start_size: int, min_size: int, bold: bool = False):
    for size in range(start_size, min_size - 1, -1):
        selected = font(size, bold=bold)
        left, top, right, bottom = draw.textbbox((0, 0), text, font=selected)
        if right - left <= max_width:
            return selected
    return font(min_size, bold=bold)


def main() -> int:
    if not SOURCE.exists():
        raise SystemExit(f"missing source icon: {SOURCE}")

    canvas = gradient()
    add_glow(canvas, (-130, -160, 420, 390), CYAN, 72)
    add_glow(canvas, (690, 180, 1180, 670), GOLD, 38)

    draw = ImageDraw.Draw(canvas, "RGBA")

    # Quiet trivia texture: visible enough to add energy, subtle enough to keep
    # the art legible when Play displays it as a small card.
    texture_font = font(72, bold=True)
    for x, y, alpha in ((24, 38, 25), (215, 395, 20), (900, 44, 24), (818, 350, 18)):
        draw.text((x, y), "?", font=texture_font, fill=(255, 255, 255, alpha))
    for x, y, radius, fill in (
        (410, 58, 5, (*GOLD, 115)),
        (448, 82, 3, (*CYAN, 140)),
        (939, 291, 5, (*WHITE, 70)),
        (760, 71, 4, (*GOLD, 90)),
    ):
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=fill)

    # Place the real shipped icon, not invented UI, so the listing art remains
    # faithful to the app even as gameplay screens evolve.
    icon = Image.open(SOURCE).convert("RGBA")
    icon.thumbnail((304, 304), Image.Resampling.LANCZOS)
    icon_x = 72
    icon_y = (HEIGHT - icon.height) // 2

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_mask = icon.getchannel("A")
    soft_mask = shadow_mask.filter(ImageFilter.GaussianBlur(18))
    shadow_patch = Image.new("RGBA", icon.size, (7, 4, 35, 160))
    shadow_patch.putalpha(soft_mask.point(lambda value: int(value * 0.62)))
    shadow.alpha_composite(shadow_patch, (icon_x + 12, icon_y + 20))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(icon, (icon_x, icon_y))

    draw = ImageDraw.Draw(canvas, "RGBA")
    text_x = 430
    max_text_width = WIDTH - text_x - 62

    eyebrow_font = font(22, bold=True)
    draw.rounded_rectangle((text_x, 79, text_x + 282, 119), radius=20, fill=(255, 255, 255, 25), outline=(255, 255, 255, 46), width=1)
    draw.text((text_x + 21, 86), "TRIVIA • DAILY • FRIENDS", font=eyebrow_font, fill=SOFT_WHITE)

    title = "EZ Trivia"
    title_font = fit_text(draw, title, max_text_width, start_size=86, min_size=66, bold=True)
    draw.text((text_x, 137), title, font=title_font, fill=WHITE, stroke_width=1, stroke_fill=(255, 255, 255, 24))

    tagline_font = font(39, bold=True)
    draw.text((text_x, 244), "Play. Learn. Compete.", font=tagline_font, fill=(255, 230, 163))

    detail_font = fit_text(
        draw,
        "2,341 questions • 16 categories • 3 difficulty levels",
        max_text_width,
        start_size=24,
        min_size=18,
    )
    draw.text(
        (text_x, 309),
        "2,341 questions • 16 categories • 3 difficulty levels",
        font=detail_font,
        fill=SOFT_WHITE,
    )

    # A short underline/callout gives the right side a finished visual anchor.
    draw.rounded_rectangle((text_x, 365, text_x + 392, 416), radius=25, fill=(14, 8, 65, 72), outline=(255, 255, 255, 34), width=1)
    callout_font = font(23, bold=True)
    draw.text((text_x + 22, 377), "Quick Play • Daily Challenge • Friend battles", font=callout_font, fill=WHITE)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    final = canvas.convert("RGB")
    if final.size != (WIDTH, HEIGHT):
        raise SystemExit(f"unexpected feature graphic size: {final.size}")
    final.save(OUTPUT, format="PNG", optimize=True)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({WIDTH}x{HEIGHT}, RGB PNG)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
