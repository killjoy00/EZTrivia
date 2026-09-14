#!/usr/bin/env python3
"""Build the Google Play 1024x500 feature graphic from EZ Trivia's shipped icon."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

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


def rounded_icon(source: Image.Image, size: int, radius: int) -> Image.Image:
    icon = source.convert("RGBA")
    icon.thumbnail((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, icon.width - 1, icon.height - 1),
        radius=radius,
        fill=255,
    )
    mask = ImageChops.multiply(icon.getchannel("A"), mask)
    icon.putalpha(mask)
    return icon


def add_texture(canvas: Image.Image) -> None:
    """Composite low-contrast trivia marks without losing alpha on RGB export."""
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    texture_font = font(66, bold=True)
    for x, y, alpha in ((30, 42, 20), (900, 46, 18), (225, 400, 12), (928, 402, 10)):
        draw.text((x, y), "?", font=texture_font, fill=(255, 255, 255, alpha))
    for x, y, radius, fill in (
        (410, 58, 5, (*GOLD, 115)),
        (448, 82, 3, (*CYAN, 140)),
        (939, 291, 5, (*WHITE, 70)),
        (760, 71, 4, (*GOLD, 90)),
    ):
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=fill)
    canvas.alpha_composite(layer)


def translucent_round_rect(
    canvas: Image.Image,
    box: tuple[int, int, int, int],
    radius: int,
    fill: tuple[int, int, int, int],
    outline: tuple[int, int, int, int] | None = None,
    width: int = 1,
) -> None:
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)
    canvas.alpha_composite(layer)


def feature_chips(
    canvas: Image.Image,
    x: int,
    y: int,
    max_width: int,
) -> None:
    labels = ("Quick Play", "Daily Challenge", "Friend battles")
    gap = 10
    horizontal_padding = 17

    measure = ImageDraw.Draw(canvas)
    selected_font = None
    widths: list[int] = []
    for size in range(19, 14, -1):
        candidate = font(size, bold=True)
        candidate_widths = []
        for label in labels:
            left, top, right, bottom = measure.textbbox((0, 0), label, font=candidate)
            candidate_widths.append((right - left) + horizontal_padding * 2)
        if sum(candidate_widths) + gap * (len(labels) - 1) <= max_width:
            selected_font = candidate
            widths = candidate_widths
            break
    if selected_font is None:
        selected_font = font(14, bold=True)
        widths = []
        for label in labels:
            left, top, right, bottom = measure.textbbox((0, 0), label, font=selected_font)
            widths.append((right - left) + horizontal_padding * 2)

    chip_height = 43
    cursor = x
    for chip_width in widths:
        translucent_round_rect(
            canvas,
            (cursor, y, cursor + chip_width, y + chip_height),
            radius=chip_height // 2,
            fill=(14, 8, 65, 125),
            outline=(255, 255, 255, 72),
        )
        cursor += chip_width + gap

    draw = ImageDraw.Draw(canvas)
    cursor = x
    for label, chip_width in zip(labels, widths):
        bbox = draw.textbbox((0, 0), label, font=selected_font)
        text_height = bbox[3] - bbox[1]
        text_y = y + (chip_height - text_height) // 2 - bbox[1]
        draw.text((cursor + horizontal_padding, text_y), label, font=selected_font, fill=WHITE)
        cursor += chip_width + gap


def main() -> int:
    if not SOURCE.exists():
        raise SystemExit(f"missing source icon: {SOURCE}")

    canvas = gradient()
    add_glow(canvas, (-130, -160, 420, 390), CYAN, 72)
    add_glow(canvas, (690, 180, 1180, 670), GOLD, 38)
    add_texture(canvas)

    # Place the real shipped icon, not invented UI, so the listing art remains
    # faithful to the app even as gameplay screens evolve.
    icon = rounded_icon(Image.open(SOURCE), size=304, radius=58)
    icon_x = 72
    icon_y = (HEIGHT - icon.height) // 2

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    soft_mask = icon.getchannel("A").filter(ImageFilter.GaussianBlur(18))
    shadow_patch = Image.new("RGBA", icon.size, (7, 4, 35, 160))
    shadow_patch.putalpha(soft_mask.point(lambda value: int(value * 0.62)))
    shadow.alpha_composite(shadow_patch, (icon_x + 12, icon_y + 20))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(icon, (icon_x, icon_y))

    draw = ImageDraw.Draw(canvas)
    text_x = 430
    max_text_width = WIDTH - text_x - 62

    eyebrow = "TRIVIA • DAILY • FRIENDS"
    eyebrow_font = fit_text(draw, eyebrow, 270, start_size=20, min_size=17, bold=True)
    eyebrow_bbox = draw.textbbox((0, 0), eyebrow, font=eyebrow_font)
    eyebrow_width = eyebrow_bbox[2] - eyebrow_bbox[0]
    pill_width = eyebrow_width + 38
    translucent_round_rect(
        canvas,
        (text_x, 79, text_x + pill_width, 119),
        radius=20,
        fill=(255, 255, 255, 30),
        outline=(255, 255, 255, 55),
    )
    draw = ImageDraw.Draw(canvas)
    draw.text((text_x + 19, 87), eyebrow, font=eyebrow_font, fill=SOFT_WHITE)

    title = "EZ Trivia"
    title_font = fit_text(draw, title, max_text_width, start_size=86, min_size=66, bold=True)
    draw.text((text_x, 137), title, font=title_font, fill=WHITE)

    tagline_font = font(39, bold=True)
    draw.text((text_x, 244), "Play. Learn. Compete.", font=tagline_font, fill=(255, 230, 163))

    detail = "2,341 questions • 16 categories • 3 difficulty levels"
    detail_font = fit_text(draw, detail, max_text_width, start_size=24, min_size=18)
    draw.text((text_x, 309), detail, font=detail_font, fill=SOFT_WHITE)

    feature_chips(canvas, text_x, 365, max_text_width)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    final = canvas.convert("RGB")
    if final.size != (WIDTH, HEIGHT):
        raise SystemExit(f"unexpected feature graphic size: {final.size}")
    final.save(OUTPUT, format="PNG", optimize=True)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({WIDTH}x{HEIGHT}, RGB PNG)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
