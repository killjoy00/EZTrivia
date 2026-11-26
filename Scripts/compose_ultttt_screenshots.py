#!/usr/bin/env python3
"""Compose Apple-size promotional images exclusively from real iOS captures."""
import hashlib
import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps, ImageStat

DEST=(1290,2796)
SCENES=[
 ("board","MASTER THE\n9-BOARD BATTLE","Think ahead across nine connected boards."),
 ("setup","SOLO, LOCAL,\nOR ONLINE","Choose how you want to play."),
 ("tutorial","LEARN THE\n9-BOARD RULES","Each square decides the next board."),
 ("statistics","TRACK YOUR\nPROGRESS","See your results, records, and streaks."),
 ("settings","LIGHT OR DARK.\nYOUR CALL.","Customize your look, sound, and feel."),
]
SOURCE=Path(os.environ["SCREENSHOT_SOURCE"])
OUTPUT=Path(os.environ["RUNNER_TEMP"])/"ultttt-appstore"
OUTPUT.mkdir(parents=True,exist_ok=True)

def font(size,bold=False):
    paths=(
     ["/System/Library/Fonts/Supplemental/Arial Bold.ttf",
      "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"] if bold
     else ["/System/Library/Fonts/Supplemental/Arial.ttf",
           "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"])
    for p in paths:
        if Path(p).exists():return ImageFont.truetype(p,size)
    raise RuntimeError("Missing system font on image compositor")

def gradient():
    w,h=DEST
    im=Image.new("RGB",DEST)
    draw=ImageDraw.Draw(im)
    for y in range(h):
        t=y/(h-1)
        draw.line((0,y,w,y),fill=(round(45+73*t),round(146-70*t),round(243-28*t)),width=1)
    return im

base=gradient()
digests=set()
for index,(scene,title,subtitle) in enumerate(SCENES,1):
    input_path=SOURCE/(scene+".png")
    if not input_path.is_file():raise RuntimeError("Missing actual simulator capture: "+scene)
    with Image.open(input_path) as src:
        if src.width<1100 or src.height<2400:
            raise RuntimeError("Unexpected iOS screenshot dimensions: "+str(src.size))
        rgb=src.convert("RGB")
        if ImageStat.Stat(rgb.resize((80,150))).stddev[0]<10:
            raise RuntimeError("Blank simulator screen: "+scene)
        app=ImageOps.fit(rgb,(1064,2300),Image.Resampling.LANCZOS)
    canvas=base.copy()
    d=ImageDraw.Draw(canvas)
    title_font=font(87,True)
    for line in title.split("\n"):
        while d.textbbox((0,0),line,font=title_font)[2]>1180 and getattr(title_font,"size",0)>60:
            title_font=font(title_font.size-2,True)
    for row,line in enumerate(title.split("\n")):
        d.text((645,57+row*106),line,font=title_font,fill="white",anchor="mt")
    subtitle_font=font(34)
    d.text((645,315),subtitle,font=subtitle_font,fill=(251,245,255),anchor="mt")
    x,y=113,430
    mask=Image.new("L",app.size,0)
    ImageDraw.Draw(mask).rounded_rectangle((0,0,app.width-1,app.height-1),radius=55,fill=255)
    shadow=Image.new("RGBA",DEST,(0,0,0,0))
    shadow.paste((13,18,62,140),(x,y+15,x+app.width,y+15+app.height),mask)
    shadow=shadow.filter(ImageFilter.GaussianBlur(24))
    canvas=Image.alpha_composite(canvas.convert("RGBA"),shadow)
    canvas.paste(app,(x,y),mask)
    output=OUTPUT/("ultttt-%02d-%s.png"%(index,scene))
    canvas.convert("RGB").save(output,"PNG",optimize=True)
    with Image.open(output) as check:
        if check.size!=DEST or check.mode!="RGB":raise RuntimeError("Bad Apple screenshot output")
    digest=hashlib.sha256(output.read_bytes()).hexdigest()
    if digest in digests:raise RuntimeError("Duplicate captures")
    digests.add(digest)
    print(output.name,DEST,output.stat().st_size,digest[:14])
print("Five screens composed from actual SwiftUI simulator captures (not the mockups).")
