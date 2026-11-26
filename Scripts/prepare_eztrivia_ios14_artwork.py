#!/usr/bin/env python3
"""Reproduce approved EZ Trivia artwork from authentic archived iOS screenshots.

Source captures: GitHub Actions run 34059574869, artifact 9997153447.
Assets are *never* an invented gameplay state. Text/headlines are marketing copy.
"""
from __future__ import annotations

import base64
import zlib
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = Path(__file__).resolve().parents[1]
SHOT = HERE / "artwork_source"
OUT = HERE / "artwork_generated"
OUT.mkdir(parents=True, exist_ok=True)
ICON = Image.open(HERE / "EZTriviaApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png").convert("RGBA")

# Very low-frequency color field sampled from the approved abstract background.
# 32x22 pixels, reconstructed with bicubic filtering; avoids external image
# dependencies and keeps the original approved visual composition.
BGR_LUT = (
"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"
)
BGR = Image.frombytes("RGB", (32,22), zlib.decompress(base64.b64decode(BGR_LUT))).resize((1536,1024),Image.Resampling.BICUBIC)

def font(size, weight="bold"):
    names = {"bold":"InterDisplay-Bold.otf","semi":"InterDisplay-SemiBold.otf","medium":"Inter-Medium.otf"}
    filename = names.get(weight, "Inter-Regular.otf")
    candidates = [Path("/usr/share/fonts/opentype/inter") / filename,
                  Path("/usr/share/fonts/truetype/inter") / filename,
                  Path("/usr/share/fonts/truetype/dejavu/DejaVuSans" + ("-Bold" if weight in ("bold","semi") else "") + ".ttf")]
    for path in candidates:
        if path.is_file(): return ImageFont.truetype(str(path),size)
    raise FileNotFoundError("Inter and fallback fonts are unavailable")

def cover(im, wh):
    w,h=wh
    scale=max(w/im.width,h/im.height)
    scaled=im.resize((round(im.width*scale),round(im.height*scale)),Image.Resampling.LANCZOS)
    return scaled.crop(((scaled.width-w)//2,(scaled.height-h)//2,(scaled.width+w)//2,(scaled.height+h)//2))

def gradient(wh, top, bottom):
    w,h=wh
    src=Image.new("RGB",(1,h)); px=src.load()
    for y in range(h):
        t=y/max(h-1,1);t=t*t*(3-2*t)
        px[0,y]=tuple(round(top[i]*(1-t)+bottom[i]*t) for i in range(3))
    return src.resize(wh)

def rounded(bg, fg, xy, wh, radius, shadow=(0,12,28,58)):
    fg=fg.resize(wh,Image.Resampling.LANCZOS).convert("RGBA")
    mask=Image.new("L",wh,0)
    ImageDraw.Draw(mask).rounded_rectangle((0,0,wh[0]-1,wh[1]-1),radius=radius,fill=255)
    fg.putalpha(mask)
    if shadow:
        dx,dy,blur,alpha=shadow
        sh=Image.new("RGBA",wh,(0,0,0,alpha));sh.putalpha(mask.point(lambda v:round(v*alpha/255)))
        full=Image.new("RGBA",bg.size)
        full.paste(sh,(xy[0]+dx,xy[1]+dy));bg.alpha_composite(full.filter(ImageFilter.GaussianBlur(blur)))
    bg.alpha_composite(fg,xy)

def icon(bg, xy, size, radius):
    rounded(bg, ICON, xy, (size,size),radius)

def dots(bg):
    d=ImageDraw.Draw(bg,"RGBA");w,h=bg.size
    d.ellipse((-560,-650,400,330),fill=(255,160,72,65))
    d.ellipse((w-390,h-300,w+620,h+700),fill=(255,53,126,55))
    d.ellipse((-500,h-240,240,h+480),fill=(0,199,239,50))
    d.ellipse((w-230,-270,w+340,290),fill=(30,230,214,40))

def screenshot(name, source, headline, subline, colors):
    bg=gradient((1320,2868),colors[0],colors[1]).convert("RGBA")
    dots(bg); icon(bg,(106,126),150,32);d=ImageDraw.Draw(bg)
    d.text((289,164),"EZ Trivia",font=font(65,"semi"),fill="white")
    y=365
    for line in headline:
        d.text((105,y),line,font=font(116),fill="white");y+=int(116*1.15)
    d.text((112,y+28),subline,font=font(45,"medium"),fill=(231,228,253))
    snap=Image.open(SHOT/source).convert("RGBA")
    w=1070;h=round(w*snap.height/snap.width)
    rounded(bg,snap,(125,1050),(w,h),66,(0,26,55,104))
    bg.convert("RGB").save(OUT/name,optimize=True)

def main():
    for n in ("01-home.png","03-answered.png","04-daily.png"):
        assert (SHOT/n).is_file(),f"Missing real screenshot {n}"
    header=cover(BGR,(3840,1646)).convert("RGBA")
    header.alpha_composite(Image.new("RGBA",header.size,(18,5,65,54)))
    icon(header,(1260,640),360,75);d=ImageDraw.Draw(header)
    d.text((1700,692),"EZ Trivia",font=font(180),fill="white")
    d.text((1710,913),"Ten quick questions. One great score.",font=font(60,"medium"),fill=(233,225,255))
    header.convert("RGB").save(OUT/"EZTrivia_product_header_3840x1646.png",optimize=True)

    search=cover(BGR,(1920,1280)).convert("RGBA")
    search.alpha_composite(Image.new("RGBA",search.size,(21,7,68,45)))
    icon(search,(356,330),150,33);d=ImageDraw.Draw(search)
    d.text((542,355),"EZ Trivia",font=font(79),fill="white")
    d.text((384,554),"Daily Challenge.",font=font(89),fill="white")
    d.text((390,686),"Ten questions. Everyone",font=font(38,"medium"),fill=(242,235,255))
    d.text((390,744),"plays the same set.",font=font(38,"medium"),fill=(242,235,255))
    daily=Image.open(SHOT/"04-daily.png").convert("RGBA").crop((0,180,1320,2280))
    rounded(search,daily,(1102,260),(585,960),30,(2,24,52,92))
    search.convert("RGB").save(OUT/"EZTrivia_search_results_1920x1280.png",optimize=True)

    screenshot("EZTrivia_screenshot_home_1320x2868.png","01-home.png",
       ["Ten quick questions.","One great score."],
       "Daily Challenge  ·  Quick Play  ·  Friend Challenge",((53,31,138),(103,33,197)))
    screenshot("EZTrivia_screenshot_daily_1320x2868.png","04-daily.png",
       ["The daily","trivia challenge."],
       "Ten questions. Everyone plays the same set.",((129,47,151),(249,72,117)))
    screenshot("EZTrivia_screenshot_explanation_1320x2868.png","03-answered.png",
       ["Make your pick.","See the answer."],
       "Real questions, with explanations.",((40,65,166),(81,41,168)))
    for f in sorted(OUT.glob("EZTrivia_*.png")):
        with Image.open(f) as image:
            print(f.name, image.size, f.stat().st_size)

if __name__ == "__main__":
    main()
