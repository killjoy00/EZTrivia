#!/usr/bin/env python3
"""Render the eleven Game Center leaderboard images.

App Store Connect wants a square RGB PNG with no alpha channel for every
leaderboard. These are drawn as SVG and rasterised with the Chromium that is
already on the machine, so the output is reproducible: rerunning the script
gives byte-comparable art rather than something hand-edited that nobody can
regenerate later.

Colours follow AppTheme.color(for:) so a leaderboard reads as the same
category the player just finished. Basketball is the one deliberate
departure -- the app paints it the same orange as football, which is fine
when the two are labelled rows in a list but reads as duplicate art when
Game Center shows eleven tiles together.

Usage:  python3 Scripts/make_leaderboard_art.py [--size 1024] [--out DIR]
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

# Chromium reserves part of --window-size for window furniture, so the
# painted viewport is shorter than the number asked for. Measured at 87px
# on this runner; the extra margin costs nothing since the tile is cropped
# out of the top-left corner afterwards.
WINDOW_CHROME = 160

CHROMIUM_CANDIDATES = [
    "/opt/pw-browsers/chromium",
    "/opt/pw-browsers/chromium-1194/chrome-linux/chrome",
    "chromium",
    "chromium-browser",
    "google-chrome",
]

# (vendor id suffix, display name, base colour, gradient partner)
CATEGORIES = [
    ("football",   "Football",       "#FF9F0A", "#C2410C"),
    ("basketball", "Basketball",     "#F97316", "#9A3412"),
    ("soccer",     "World Flags",    "#34C759", "#15803D"),  # placeholder, fixed below
    ("flags",      "World Flags",    "#0A84FF", "#1D4ED8"),
    ("history",    "History",        "#A2845E", "#6B4F2A"),
    ("science",    "Science",        "#AF52DE", "#6D28D9"),
    ("movies",     "Movies",         "#FF375F", "#9F1239"),
    ("tv",         "TV",             "#00C7BE", "#0F766E"),
    ("geography",  "Geography",      "#32ADE6", "#0E7490"),
    ("music",      "Music",          "#5E5CE6", "#3730A3"),
    ("animals",    "Animals",        "#30B0C7", "#0F766E"),
    ("food",       "Food & Drink",   "#FF453A", "#B91C1C"),
    # Orange-to-pink rather than a category colour: it matches the Daily
    # Challenge card's gradient in the app (RootView), so the leaderboard
    # reads as the same feature rather than a twelfth topic.
    ("daily",      "Daily Challenge","#FF9500", "#D6336C"),
]
CATEGORIES[2] = ("soccer", "Soccer", "#34C759", "#15803D")

# Each icon is drawn in a 200x200 box. Cut-outs (laces, seams, sprocket holes)
# are punched with a mask so the gradient shows through them, rather than being
# painted in a flat colour that would not match a gradient background.
ICONS: dict[str, str] = {
    "football": """
      <mask id="cut">
        <rect width="200" height="200" fill="white"/>
        <g stroke="black" stroke-width="9" stroke-linecap="round">
          <line x1="58" y1="100" x2="142" y2="100"/>
          <line x1="72" y1="87" x2="72" y2="113"/>
          <line x1="90" y1="85" x2="90" y2="115"/>
          <line x1="110" y1="85" x2="110" y2="115"/>
          <line x1="128" y1="87" x2="128" y2="113"/>
        </g>
      </mask>
      <g transform="rotate(-28 100 100)">
        <ellipse cx="100" cy="100" rx="86" ry="52" fill="white" mask="url(#cut)"/>
      </g>""",
    "basketball": """
      <mask id="cut">
        <rect width="200" height="200" fill="white"/>
        <g stroke="black" stroke-width="9" fill="none" stroke-linecap="round">
          <line x1="100" y1="12" x2="100" y2="188"/>
          <line x1="12" y1="100" x2="188" y2="100"/>
          <path d="M40,30 Q78,100 40,170"/>
          <path d="M160,30 Q122,100 160,170"/>
        </g>
      </mask>
      <circle cx="100" cy="100" r="88" fill="white" mask="url(#cut)"/>""",
    "soccer": """
      <mask id="cut">
        <rect width="200" height="200" fill="white"/>
        <path d="M100,58 L133,82 L120,121 L80,121 L67,82 Z" fill="black"/>
        <g stroke="black" stroke-width="9" stroke-linecap="round">
          <line x1="100" y1="58" x2="100" y2="16"/>
          <line x1="133" y1="82" x2="174" y2="66"/>
          <line x1="120" y1="121" x2="150" y2="158"/>
          <line x1="80" y1="121" x2="50" y2="158"/>
          <line x1="67" y1="82" x2="26" y2="66"/>
        </g>
      </mask>
      <circle cx="100" cy="100" r="88" fill="white" mask="url(#cut)"/>""",
    "flags": """
      <rect x="30" y="16" width="15" height="170" rx="7" fill="white"/>
      <path d="M45,34 C82,12 118,52 158,32 L158,104 C118,124 82,84 45,106 Z" fill="white"/>""",
    "history": """
      <path d="M100,16 L182,58 L182,74 L18,74 L18,58 Z" fill="white"/>
      <g fill="white">
        <rect x="34" y="84" width="20" height="80" rx="5"/>
        <rect x="74" y="84" width="20" height="80" rx="5"/>
        <rect x="114" y="84" width="20" height="80" rx="5"/>
        <rect x="154" y="84" width="20" height="80" rx="5"/>
      </g>
      <rect x="16" y="170" width="168" height="16" rx="7" fill="white"/>""",
    "science": """
      <g fill="none" stroke="white" stroke-width="11">
        <ellipse cx="100" cy="100" rx="88" ry="34"/>
        <ellipse cx="100" cy="100" rx="88" ry="34" transform="rotate(60 100 100)"/>
        <ellipse cx="100" cy="100" rx="88" ry="34" transform="rotate(120 100 100)"/>
      </g>
      <circle cx="100" cy="100" r="19" fill="white"/>""",
    "tv": """
      <rect x="24" y="44" width="152" height="102" rx="14" fill="none" stroke="white" stroke-width="12"/>
      <path d="M70 26 L100 56 L130 26" fill="none" stroke="white" stroke-width="12"
            stroke-linecap="round" stroke-linejoin="round"/>
      <rect x="62" y="164" width="76" height="12" rx="6" fill="white"/>""",
    "movies": """
      <mask id="cut">
        <rect width="200" height="200" fill="white"/>
        <g fill="black">
          <rect x="26" y="46" width="20" height="20" rx="5"/>
          <rect x="26" y="90" width="20" height="20" rx="5"/>
          <rect x="26" y="134" width="20" height="20" rx="5"/>
          <rect x="154" y="46" width="20" height="20" rx="5"/>
          <rect x="154" y="90" width="20" height="20" rx="5"/>
          <rect x="154" y="134" width="20" height="20" rx="5"/>
          <rect x="62" y="42" width="76" height="52" rx="7"/>
          <rect x="62" y="106" width="76" height="52" rx="7"/>
        </g>
      </mask>
      <rect x="14" y="28" width="172" height="144" rx="20" fill="white" mask="url(#cut)"/>""",
    "geography": """
      <mask id="cut">
        <rect width="200" height="200" fill="white"/>
        <g fill="none" stroke="black" stroke-width="9">
          <ellipse cx="100" cy="100" rx="36" ry="88"/>
          <line x1="14" y1="100" x2="186" y2="100"/>
          <path d="M28,62 H172"/>
          <path d="M28,138 H172"/>
        </g>
      </mask>
      <circle cx="100" cy="100" r="88" fill="white" mask="url(#cut)"/>""",
    "music": """
      <g fill="white">
        <ellipse cx="56" cy="152" rx="34" ry="26" transform="rotate(-18 56 152)"/>
        <ellipse cx="140" cy="130" rx="34" ry="26" transform="rotate(-18 140 130)"/>
        <rect x="78" y="40" width="14" height="112" rx="6"/>
        <rect x="162" y="18" width="14" height="112" rx="6"/>
        <path d="M78,40 L176,18 L176,54 L78,76 Z"/>
      </g>""",
    "animals": """
      <g fill="white">
        <ellipse cx="100" cy="140" rx="52" ry="42"/>
        <ellipse cx="46" cy="88" rx="24" ry="30" transform="rotate(-20 46 88)"/>
        <ellipse cx="154" cy="88" rx="24" ry="30" transform="rotate(20 154 88)"/>
        <ellipse cx="78" cy="42" rx="22" ry="28"/>
        <ellipse cx="126" cy="42" rx="22" ry="28"/>
      </g>""",
    "food": """
      <g fill="white">
        <rect x="34" y="14" width="12" height="60" rx="5"/>
        <rect x="58" y="14" width="12" height="60" rx="5"/>
        <path d="M26,66 H78 A26,26 0 0,1 58,98 L58,182 A6,6 0 0,1 46,182 L46,98 A26,26 0 0,1 26,66 Z"/>
        <path d="M150,14 C176,40 176,84 156,102 L156,182 A6,6 0 0,1 144,182 L144,110 C126,104 126,40 150,14 Z"/>
      </g>""",
    # A flame, matching the streak icon players already see in the app
    # (DailyChallengeCard / DailyResultView both use flame.fill), so the
    # leaderboard is recognisable as "the daily" at a glance rather than
    # needing its name read first.
    "daily": """
      <path d="M100,16
               C74,52 58,78 58,112
               C58,150 78,178 100,178
               C122,178 142,150 142,112
               C142,90 130,70 118,56
               C121,76 111,90 98,90
               C86,90 80,78 82,64
               C84,48 90,32 100,16 Z"
            fill="white"/>
      <path d="M100,110
               C90,122 86,136 86,148
               C86,164 92,174 100,174
               C108,174 114,164 114,148
               C114,138 110,126 102,116
               C104,126 100,134 96,134
               C91,134 88,126 92,116
               C95,113 98,111 100,110 Z"
            fill="#00000026"/>""",
}

PAGE = """<!doctype html>
<meta charset="utf-8">
<style>
  html, body {{ margin: 0; padding: 0; background: {base}; overflow: hidden; }}
  .tile {{
    width: {size}px; height: {size}px;
    background: linear-gradient(135deg, {base} 0%, {dark} 100%);
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    position: relative; overflow: hidden;
  }}
  /* A soft highlight off the top-left keeps the flat gradient from looking
     like a solid colour swatch once Game Center scales the tile down. */
  .tile::before {{
    content: ""; position: absolute; inset: 0;
    background: radial-gradient(circle at 26% 18%,
                rgba(255,255,255,.28) 0%, rgba(255,255,255,0) 58%);
  }}
  svg {{ width: {icon}px; height: {icon}px; position: relative; }}
  .wordmark {{
    position: absolute; bottom: {wordbottom}px; left: 0; right: 0;
    text-align: center;
    font-family: "Liberation Sans", Helvetica, Arial, sans-serif;
    font-weight: 700; font-size: {wordsize}px;
    letter-spacing: {wordspace}px;
    color: rgba(255,255,255,.62);
  }}
</style>
<div class="tile">
  <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">{icon_svg}</svg>
  <div class="wordmark">EZ TRIVIA</div>
</div>
"""


def find_chromium() -> str:
    for candidate in CHROMIUM_CANDIDATES:
        if candidate.startswith("/"):
            if Path(candidate).is_file():
                return candidate
        else:
            found = shutil.which(candidate)
            if found:
                return found
    sys.exit(
        "No Chromium binary found. Looked at:\n  "
        + "\n  ".join(CHROMIUM_CANDIDATES)
    )


def render(chromium: str, html: str, png: Path, size: int) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "tile.html"
        page.write_text(html, encoding="utf-8")
        subprocess.run(
            [
                chromium,
                "--headless",
                "--no-sandbox",
                "--disable-gpu",
                "--hide-scrollbars",
                "--force-device-scale-factor=1",
                "--default-background-color=00000000",
                f"--window-size={size},{size + WINDOW_CHROME}",
                f"--screenshot={png}",
                page.as_uri(),
            ],
            check=True,
            capture_output=True,
        )


def flatten(png: Path, size: int) -> None:
    """Drop the alpha channel and pin the size.

    App Store Connect rejects a leaderboard image that carries an alpha
    channel, and Chromium always writes RGBA. Compositing onto the tile's own
    top-left colour rather than onto white means an anti-aliased edge cannot
    pick up a pale halo.
    """
    from PIL import Image

    with Image.open(png) as img:
        img = img.convert("RGBA")
        if img.size[0] < size or img.size[1] < size:
            raise SystemExit(
                f"{png.name}: Chromium returned {img.size}, smaller than the "
                f"requested {size}x{size}. Raise WINDOW_CHROME."
            )
        img = img.crop((0, 0, size, size))
        rgb = img.convert("RGB")
        corners = [rgb.getpixel(xy) for xy in
                   ((0, 0), (size - 1, 0), (0, size - 1), (size - 1, size - 1))]
        if any(c == (255, 255, 255) for c in corners):
            raise SystemExit(
                f"{png.name}: a corner is white, so the capture is padding "
                f"rather than tile. Raise WINDOW_CHROME."
            )
        backdrop = Image.new("RGB", img.size, corners[0])
        backdrop.paste(img, mask=img.split()[3])
        backdrop.save(png, "PNG", dpi=(72, 72), optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--size", type=int, default=1024,
                        help="output edge in pixels (Apple accepts 512 or 1024)")
    parser.add_argument("--out", type=Path,
                        default=Path("Artwork/Leaderboards"),
                        help="directory to write the PNGs into")
    args = parser.parse_args()

    size = args.size
    args.out.mkdir(parents=True, exist_ok=True)
    chromium = find_chromium()

    for slug, name, base, dark in CATEGORIES:
        html = PAGE.format(
            size=size,
            base=base,
            dark=dark,
            icon=round(size * 0.46),
            icon_svg=ICONS[slug],
            wordbottom=round(size * 0.088),
            wordsize=round(size * 0.043),
            wordspace=round(size * 0.011),
        )
        png = args.out / f"EZTrivia.{slug}.png"
        render(chromium, html, png, size)
        flatten(png, size)
        print(f"{png}  {name}  {base}")

    print(f"\n{len(CATEGORIES)} leaderboard images written to {args.out}/")


if __name__ == "__main__":
    main()
