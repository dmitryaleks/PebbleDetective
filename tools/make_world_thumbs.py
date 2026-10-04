#!/usr/bin/env python3
"""Cut the bundled world photographs down to table-sized discs.

The colour table in the README says which stone belongs to which world.
It is a good deal more use with the world in it, so each row gets the
actual photograph the app would show, trimmed to its limb and masked to
a circle - the same two steps PlanetArt does on the phone.

Transparent background on purpose: GitHub renders the README on a light
page or a dark one depending on the reader, and a disc with no backing
works on both.

Usage:  python tools/make_world_thumbs.py
"""
from __future__ import annotations

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLANETS = os.path.join(ROOT, "app", "src", "main", "assets", "planets")
OUT = os.path.join(ROOT, "docs", "worlds")

SIZE = 96
# Everything a stone can be attributed to. Earth is not among them: it is
# where the pebble lands, never where it came from.
WORLDS = [
    "sun", "mercury", "venus", "moon", "mars",
    "jupiter", "saturn", "uranus", "neptune",
]

# Above the level the app treats as empty sky, so one stray hot pixel in a
# corner cannot widen the crop to the whole frame.
TRIM_LEVEL = 26

# The Sun fills its own frame edge to edge, so keying its black away
# leaves nothing to key.
CORNER_LIT = {"sun"}


def disc(name: str) -> Image.Image:
    source = Image.open(os.path.join(PLANETS, name + ".jpg")).convert("RGB")

    # Trim to the body. The library frames do not all fill their own
    # picture, and a disc centred on the frame rather than on the planet
    # comes out lopsided.
    box = source.convert("L").point(lambda v: 255 if v > TRIM_LEVEL else 0).getbbox()
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    half = max(box[2] - box[0], box[3] - box[1]) / 2
    body = source.crop((int(cx - half), int(cy - half), int(cx + half), int(cy + half)))
    body = body.resize((SIZE, SIZE), Image.LANCZOS)

    # Alpha from luminance rather than a circular crop, which is what the
    # app does and what keeps Saturn: a circle cuts the ring tips off, and
    # the rings are the only reason anyone recognises it at this size.
    luma = body.convert("L")
    alpha = luma.point(lambda v: 0 if v < 16 else min(255, int((v - 16) * 7)))

    out = body.convert("RGBA")
    out.putalpha(alpha)

    if name in CORNER_LIT:
        # Lit to the corners of its own frame, so keying alone leaves a
        # bright square. This one wants the circle after all.
        mask = Image.new("L", (SIZE * 4, SIZE * 4), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, SIZE * 4 - 1, SIZE * 4 - 1], fill=255)
        out.putalpha(Image.composite(alpha, Image.new("L", alpha.size, 0),
                                     mask.resize((SIZE, SIZE), Image.LANCZOS)))
    return out


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    print("worlds:")
    for name in WORLDS:
        path = os.path.join(OUT, name + ".png")
        disc(name).save(path, "PNG", optimize=True)
        print("  %-9s %d kB" % (name, os.path.getsize(path) // 1024))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
