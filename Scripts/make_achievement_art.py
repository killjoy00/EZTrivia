#!/usr/bin/env python3
"""Generate the thirteen 1024px Game Center achievement images."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


SIZE = 1024
OUTPUT = Path(__file__).resolve().parents[1] / "Artwork" / "Achievements"
FONT = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")

ACHIEVEMENTS = {
    "first_round": ("1", "FIRST ROUND"),
    "perfect_easy": ("E", "EASY DOES IT"),
    "perfect_medium": ("M", "PERFECTLY BALANCED"),
    "perfect_hard": ("H", "HARD TO BEAT"),
    "all_categories": ("12", "ALL CATEGORIES"),
    "all_categories_14": ("14", "ALL CATEGORIES"),
    "streak_7": ("7", "DAY STREAK"),
    "streak_30": ("30", "DAY STREAK"),
    "rounds_10": ("10", "ROUNDS"),
    "rounds_50": ("50", "ROUNDS"),
    "rounds_100": ("100", "ROUNDS"),
    "points_10000": ("10K", "LIFETIME POINTS"),
    "points_50000": ("50K", "LIFETIME POINTS"),
}


def font(size: int):
    return ImageFont.truetype(str(FONT), size) if FONT.exists() else ImageFont.load_default()


def centered(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str, face, fill):
    box = draw.textbbox((0, 0), text, font=face)
    width = box[2] - box[0]
    height = box[3] - box[1]
    draw.text((xy[0] - width / 2, xy[1] - height / 2 - box[1]), text, font=face, fill=fill)


def make_image(label: str, title: str) -> Image.Image:
    image = Image.new("RGB", (SIZE, SIZE))
    pixels = image.load()
    top = (67, 56, 202)
    bottom = (147, 51, 234)
    for y in range(SIZE):
        t = y / (SIZE - 1)
        color = tuple(round(a * (1 - t) + b * t) for a, b in zip(top, bottom))
        for x in range(SIZE):
            # A subtle left-to-right lift keeps the flat gradient from looking
            # like a placeholder while remaining legible at Game Center sizes.
            lift = round(14 * x / (SIZE - 1))
            pixels[x, y] = tuple(min(255, component + lift) for component in color)

    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((92, 92, 932, 932), radius=190, outline=(255, 255, 255), width=10)
    draw.ellipse((238, 220, 786, 768), fill=(255, 255, 255), outline=(232, 229, 255), width=16)
    centered(draw, (512, 156), "EZ TRIVIA", font(54), (255, 255, 255))
    label_size = 250 if len(label) <= 2 else 190
    centered(draw, (512, 495), label, font(label_size), (79, 70, 229))
    centered(draw, (512, 858), title, font(48 if len(title) < 17 else 40), (255, 255, 255))
    return image


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for identifier, (label, title) in ACHIEVEMENTS.items():
        path = OUTPUT / f"EZTrivia.achievement.{identifier}.png"
        make_image(label, title).save(path, format="PNG", dpi=(72, 72), optimize=True)
        print(path.relative_to(OUTPUT.parents[1]))


if __name__ == "__main__":
    main()
