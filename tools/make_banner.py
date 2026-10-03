#!/usr/bin/env python3
"""Compose the README hero banner.

Builds a starfield, lays the bundled NASA bodies along a shallow arc
receding into the distance, drops a drawn pebble in the foreground with a
trail back toward them, and sets the title over the top.

Everything is generated from assets already in the repo, so re-running it
reproduces the banner exactly.

Usage:  python tools/make_banner.py
"""
from __future__ import annotations

import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLANETS = os.path.join(ROOT, "app", "src", "main", "assets", "planets")
OUT = os.path.join(ROOT, "docs", "banner.png")

W, H = 1600, 640
GREEN = (77, 255, 166)
AMBER = (255, 198, 92)

FONT_DIR = "C:/Windows/Fonts"


def font(name: str, size: int) -> ImageFont.FreeTypeFont:
    path = os.path.join(FONT_DIR, name)
    if os.path.exists(path):
        return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def starfield(image: Image.Image, rng: random.Random) -> None:
    draw = ImageDraw.Draw(image)
    for _ in range(900):
        x, y = rng.randrange(W), rng.randrange(H)
        # Most stars are faint; a few are bright. Uniform brightness reads
        # as noise rather than sky.
        brightness = rng.choice([70, 90, 110, 140, 180, 230])
        size = 1 if brightness < 150 else rng.choice([1, 2])
        draw.ellipse([x, y, x + size, y + size], fill=(brightness,) * 3)

    # A faint nebula wash so the background is not flat black.
    glow = Image.new("RGB", (W, H), (0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse([-200, 180, 700, 900], fill=(16, 28, 58))
    gdraw.ellipse([1000, -220, 1900, 500], fill=(34, 20, 48))
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    image.alpha_composite(glow.convert("RGBA"))


def disc(name: str, diameter: int) -> Image.Image:
    """A planet photo lifted off its black background.

    The NASA frames are a body on black space. A plain circular crop leaves
    the black corners *and* a black ring around smaller bodies, so the alpha
    is keyed off luminance instead: deep black becomes transparent and the
    lit limb softens naturally into the starfield. A circular mask is still
    applied on top to guarantee no square edge survives.
    """
    src = Image.open(os.path.join(PLANETS, f"{name}.jpg")).convert("RGB")
    side = min(src.size)
    src = src.crop((
        (src.width - side) // 2,
        (src.height - side) // 2,
        (src.width + side) // 2,
        (src.height + side) // 2,
    )).resize((diameter, diameter), Image.LANCZOS)

    luma = src.convert("L")
    # Below ~16 is space; ramp quickly to fully opaque so planet interiors
    # never go translucent.
    alpha = luma.point(lambda v: 0 if v < 16 else min(255, int((v - 16) * 7)))

    circle = Image.new("L", (diameter * 4, diameter * 4), 0)
    ImageDraw.Draw(circle).ellipse([0, 0, diameter * 4, diameter * 4], fill=255)
    circle = circle.resize((diameter, diameter), Image.LANCZOS)

    out = src.convert("RGBA")
    out.putalpha(Image.composite(alpha, Image.new("L", alpha.size, 0), circle))
    return out


def pebble(size: int, rng: random.Random) -> Image.Image:
    """A chunky irregular rock, drawn rather than photographed."""
    scale = 4
    s = size * scale
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    points = []
    facets = 11
    for i in range(facets):
        angle = (i / facets) * 2 * math.pi
        wobble = 0.74 + 0.26 * abs(math.sin(angle * 2.3 + 1.1))
        r = s * 0.46 * wobble
        points.append((s / 2 + math.cos(angle) * r, s / 2 + math.sin(angle) * r))

    draw.polygon(points, fill=(150, 159, 175, 255))
    # Lit face.
    draw.polygon(
        [(x - s * 0.05, y - s * 0.06) for x, y in points[: facets // 2 + 2]],
        fill=(196, 205, 220, 255),
    )
    for _ in range(14):
        cx, cy = rng.uniform(s * 0.3, s * 0.7), rng.uniform(s * 0.3, s * 0.7)
        rr = rng.uniform(s * 0.03, s * 0.07)
        draw.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(118, 127, 143, 190))
    draw.line(points + [points[0]], fill=(222, 230, 240, 255), width=int(s * 0.015))

    return img.resize((size, size), Image.LANCZOS)


def main() -> int:
    rng = random.Random(7)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)

    canvas = Image.new("RGBA", (W, H), (3, 4, 10, 255))
    starfield(canvas, rng)

    # Bodies along a shallow arc, smaller and dimmer as they recede.
    arc = [
        ("neptune", 92, 1290, 196),
        ("uranus", 104, 1130, 150),
        ("saturn", 150, 940, 196),
        ("jupiter", 178, 724, 150),
        ("mars", 126, 536, 236),
        ("venus", 96, 404, 170),
        ("mercury", 78, 300, 250),
        ("sun", 210, 104, 108),
    ]
    for name, size, x, y in arc:
        body = disc(name, size)
        if name == "sun":
            halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            ImageDraw.Draw(halo).ellipse(
                [x - 60, y - 60, x + size + 60, y + size + 60], fill=(255, 150, 40, 70)
            )
            canvas.alpha_composite(halo.filter(ImageFilter.GaussianBlur(50)))
        canvas.alpha_composite(body, (x, y))

    # Earth, large and close, bottom right: the destination.
    earth = disc("earth", 430)
    canvas.alpha_composite(earth, (1240, 330))

    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)

    # The pebble's trail, curving from the far planets toward Earth.
    trail = [
        (380, 300), (520, 352), (680, 392), (840, 420),
        (980, 438), (1120, 448), (1250, 452),
    ]
    for i in range(len(trail) - 1):
        alpha = int(40 + 120 * (i / (len(trail) - 2)))
        od.line([trail[i], trail[i + 1]], fill=AMBER + (alpha,), width=3 + i // 2)
    canvas.alpha_composite(overlay.filter(ImageFilter.GaussianBlur(3)))

    rock = pebble(132, rng)
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([1150, 330, 1370, 550], fill=AMBER + (70,))
    canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(45)))
    canvas.alpha_composite(rock, (1196, 376))

    # Targeting reticle around the pebble, echoing the capture screen.
    rd = ImageDraw.Draw(canvas)
    cx, cy, r = 1262, 442, 104
    rd.ellipse([cx - r, cy - r, cx + r, cy + r], outline=GREEN + (90,), width=2)
    arm, gap = 34, 128
    for sx in (-1, 1):
        for sy in (-1, 1):
            px, py = cx + sx * gap, cy + sy * gap
            rd.line([(px, py), (px - sx * arm, py)], fill=GREEN, width=4)
            rd.line([(px, py), (px, py - sy * arm)], fill=GREEN, width=4)

    title = font("bahnschrift.ttf", 104)
    sub = font("bahnschrift.ttf", 36)
    tag = font("bahnschrift.ttf", 27)

    rd.text((92, 372), "PEBBLE", font=title, fill=(238, 244, 252))
    rd.text((92, 466), "DETECTIVE", font=title, fill=GREEN)
    rd.text((98, 332), "EVERY STONE CAME FROM SOMEWHERE", font=tag, fill=AMBER)
    rd.text((98, 580), "Find a pebble.  Scan it.  Discover which world it fell from.",
            font=sub, fill=(150, 163, 184))

    canvas.convert("RGB").save(OUT, "PNG", optimize=True)
    print(f"wrote {OUT}  ({os.path.getsize(OUT)//1024} kB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
