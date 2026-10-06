#!/usr/bin/env python3
"""Package the supplied palm-island logo and policy HTML. Requires Pillow."""
from pathlib import Path
from html import escape
import base64
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "release" / "assets"
RES = ROOT / "app" / "src" / "main" / "res"
BACKGROUND = "#FFFDF7"
INK = "#243D32"


def logo_mark():
    source = Image.open(ASSETS / "scrollxp-logo-mark.png").convert("RGBA")
    # Ignore near-invisible alpha specks for layout while retaining source alpha.
    bounds = source.getchannel("A").point(lambda a: 255 if a > 32 else 0).getbbox()
    if not bounds: raise ValueError("Logo has no visible artwork")
    return source.crop(bounds)


def artwork(size, background=BACKGROUND, fraction=0.82):
    image = Image.new("RGBA", (size, size), background)
    mark = logo_mark()
    mark.thumbnail((round(size*fraction), round(size*fraction)), Image.Resampling.LANCZOS)
    image.alpha_composite(mark, ((size-mark.width)//2, (size-mark.height)//2))
    return image


def main():
    ASSETS.mkdir(parents=True, exist_ok=True)
    nodpi = RES / "drawable-nodpi"; nodpi.mkdir(exist_ok=True)
    logo_mark().save(nodpi / "scrollxp_logo.png")
    # 58/108 keeps the complete mark inside the adaptive 66dp safe circle.
    foreground = artwork(1080, background=(0,0,0,0), fraction=58/108)
    foreground.save(nodpi / "launcher_foreground.png")
    monochrome = Image.new("RGBA", foreground.size, "white")
    monochrome.putalpha(foreground.getchannel("A"))
    monochrome.save(nodpi / "launcher_monochrome.png")
    for layer in ['foreground', 'monochrome']:
        (RES / f"drawable/ic_launcher_{layer}.xml").write_text(
            '<bitmap xmlns:android="http://schemas.android.com/apk/res/android" '
            f'android:src="@drawable/launcher_{layer}" android:gravity="fill" android:filter="true" />\n')
    vector = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
    (RES / "drawable/ic_launcher_background.xml").write_text(vector + f'    <path android:fillColor="{BACKGROUND}" android:pathData="M0,0h108v108h-108z" />\n</vector>\n')
    adaptive = '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n{mono}</adaptive-icon>\n'
    modern = RES / "mipmap-anydpi-v33"; modern.mkdir(exist_ok=True)
    for name in ['ic_launcher.xml','ic_launcher_round.xml']:
        (RES / "mipmap-anydpi" / name).write_text(adaptive.format(mono=''))
        (modern / name).write_text(adaptive.format(mono='    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'))
    artwork(512).save(ASSETS / "play-icon-512.png")
    # Compatibility SVG embeds the raster artwork; it is not a vector master.
    encoded = base64.b64encode((ASSETS / "play-icon-512.png").read_bytes()).decode("ascii")
    svg = '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">\n' + f'<image width="512" height="512" href="data:image/png;base64,{encoded}"/>\n</svg>\n'
    (ASSETS / "scrollxp-icon.svg").write_text(svg)
    for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        folder = RES / f"mipmap-{density}"; folder.mkdir(exist_ok=True)
        for name in ['ic_launcher','ic_launcher_round']:
            artwork(size).save(folder / f"{name}.webp", lossless=True)
    feature = Image.new('RGB',(1024,500),'#F6F4EE')
    draw=ImageDraw.Draw(feature)
    serif='/System/Library/Fonts/Supplemental/Georgia Bold.ttf'
    sans='/System/Library/Fonts/Supplemental/Arial.ttf'
    bold='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
    draw.text((52,70),'ScrollXP',font=ImageFont.truetype(bold,54),fill=INK)
    draw.multiline_text((52,161),'Small moments.\nA world that grows.',font=ImageFont.truetype(serif,32),spacing=14,fill=INK)
    draw.text((54,295),'Turn your screen time into progress.',font=ImageFont.truetype(sans,21),fill='#59695F')
    draw.rounded_rectangle((52,365,378,417),radius=26,fill='#DFE9D7')
    draw.text((74,381),'Build  ·  Balance  ·  Discover',font=ImageFont.truetype(sans,20),fill=INK)
    scene=artwork(460,background=(0,0,0,0),fraction=0.94)
    draw.ellipse((550,55,1000,505),fill='#E4EADD')
    feature.paste(scene,(535,0),scene)
    feature.save(ASSETS / 'feature-graphic-1024x500.png')
    markdown=(ROOT / 'release/policy/privacy-policy.md').read_text()
    body=[]
    for paragraph in markdown.split('\n\n'):
        if paragraph.startswith('# '):body.append('<h1>'+escape(paragraph[2:])+'</h1>')
        elif paragraph.startswith('## '):body.append('<h2>'+escape(paragraph[3:])+'</h2>')
        elif paragraph.strip():body.append('<p>'+escape(paragraph).replace('\n','<br>')+'</p>')
    directory=ROOT/'release/privacy';directory.mkdir(exist_ok=True)
    (directory/'index.html').write_text('<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ScrollXP Privacy Policy</title><style>body{background:#f6f4ee;color:#243d32;font:17px/1.65 system-ui,sans-serif;max-width:760px;margin:auto;padding:32px 22px}h1{line-height:1.2}h2{font-size:21px;margin-top:36px}p{overflow-wrap:anywhere}</style><main>'+''.join(body)+'</main></html>\n')
    print('Generated adaptive/themed icon, Play icon, feature graphic, and privacy HTML.')


if __name__ == '__main__':main()
