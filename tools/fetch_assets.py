#!/usr/bin/env python3
"""Download PebbleDetective's bundled media and write ASSETS.md.

Two sources, both chosen because they need no account and carry no
attribution obligation:

  * planet photos - NASA Image and Video Library (public domain)
  * the Earth the journey flies to - NASA DSCOVR/EPIC (public domain)
  * sound cues    - OpenGameArt "60 CC0 Sci-Fi SFX" (CC0)

Planet IDs are pinned, not searched. A live search by body name
returns the wrong picture often enough to matter - it gave a Kennedy
Space Center photo for Mars - so the asset set is fixed and
reproducible instead.

Usage:  python tools/fetch_assets.py [--skip-audio] [--skip-planets]

The Earth step needs Pillow; the rest deliberately does not, so a plain
Python can still refresh the planets and the sound.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import shutil
import struct
import urllib.parse
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLANET_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "planets")
AUDIO_DIR = os.path.join(ROOT, "app", "src", "main", "res", "raw")
UA = {"User-Agent": "PebbleDetective-asset-fetcher/1.0"}

# body -> (pinned nasa_id, what the picture is)
#
# The IDs are pinned rather than searched: a live search by body name
# returned a Kennedy Space Center photo for Mars and a wide strip for
# Jupiter. Pinning makes the asset set reproducible and reviewable.
BODIES = {
    "sun": ("PIA03149", "sun full disk"),
    "mercury": ("PIA15160", "Mercury MESSENGER global mosaic"),
    "venus": ("PIA00271", "Venus Magellan global view"),
    "earth": ("PIA18033", "Earth full disk"),
    "moon": ("GSFC_20171208_Archive_e001861", "Moon full disk, LRO"),
    "mars": ("PIA00407", "Mars Viking global mosaic"),
    "jupiter": ("PIA22946", "Jupiter Juno"),
    "saturn": ("PIA11141", "Saturn Cassini"),
    "uranus": ("PIA18182", "Uranus Voyager 2"),
    "neptune": ("PIA01492", "Neptune Voyager 2"),
}

# cue -> (file in the CC0 pack, why this one)
CUES = {
    "cue_ui_tap": ("sfx_09a.ogg", "0.13s tick - button presses"),
    "cue_reticle_lock": ("sfx_20a.ogg", "0.21s blip - reticle snaps onto the pebble"),
    "cue_shutter": ("sfx_07a.ogg", "0.22s snap - target captured"),
    "cue_popup_open": ("sfx_10a.ogg", "0.30s rise - Deep Research prompt appears"),
    "cue_satellite_ping": ("sfx_22a.ogg", "0.33s beep - repeated while acquiring signal"),
    "cue_signal_acquired": ("sfx_13a.ogg", "0.62s confirm - signal locked"),
    "cue_progress_complete": ("sfx_01a.ogg", "0.69s success - research finished"),
    "cue_matrix_hum": ("sfx_11c.ogg", "8.9s bed - looped under the thinking sequence"),
    "cue_launch_whoosh": ("sfx_06.ogg", "2.2s whoosh - pebble leaves the planet"),
    "cue_space_drone": ("sfx_11d.ogg", "8.7s bed - looped during the cruise"),
    "cue_entry_rumble": ("sfx_18a.ogg", "3.4s rumble - atmospheric entry"),
    "cue_arrival_chime": ("sfx_16a.ogg", "2.6s chime - landed on Earth"),

    # Detection mode: a robotic scanner idling before anything is tapped.
    "cue_scanner_ambient": ("sfx_11b.ogg", "6.5s bed - looped while the camera hunts"),
    "cue_scanner_blip": ("sfx_12a.ogg", "0.41s chirp - periodic scanner sample"),
    "cue_servo": ("sfx_14a.ogg", "0.43s servo - the reticle motor hunting"),

    # Radar mode.
    "cue_radar_ambient": ("sfx_11a.ogg", "6.9s bed - looped under the scope"),
    "cue_radar_ping": ("sfx_20b.ogg", "0.20s ping - once per sweep revolution"),
    "cue_radar_close": ("sfx_09b.ogg", "0.12s tick - proximity beep, faster when nearer"),
}

SFX_ZIP_URL = "https://opengameart.org/sites/default/files/60-sci-fi-sfx_0.zip"
SFX_PAGE = "https://opengameart.org/content/60-cc0-sci-fi-sfx"


def get(url, binary=False):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=120) as r:
        raw = r.read()
    return raw if binary else raw.decode("utf-8", "replace")



def nasa_image_url(nasa_id):
    """Prefer the ~medium rendition; it is already about 1000px wide."""
    url = "https://images-api.nasa.gov/asset/" + urllib.parse.quote(nasa_id)
    hrefs = [i["href"] for i in json.loads(get(url))["collection"]["items"]]
    for suffix in ("~medium.jpg", "~small.jpg", "~orig.jpg"):
        for h in hrefs:
            if h.endswith(suffix):
                return h.replace("http://", "https://")
    return None


def jpeg_size(data):
    """Width and height straight out of the SOF marker."""
    i = 2
    while i < len(data) - 9:
        if data[i] != 0xFF:
            i += 1
            continue
        marker = data[i + 1]
        if marker in (0xC0, 0xC1, 0xC2, 0xC3):
            h, w = struct.unpack(">HH", data[i + 5:i + 9])
            return w, h
        if marker in (0xD8, 0xD9) or 0xD0 <= marker <= 0xD7:
            i += 2
            continue
        i += 2 + struct.unpack(">H", data[i + 2:i + 4])[0]
    return 0, 0


def fetch_planets():
    os.makedirs(PLANET_DIR, exist_ok=True)
    rows = []
    for body in BODIES:
        nasa_id, caption = BODIES[body]
        print("  " + body)
        src = nasa_image_url(nasa_id)
        if not src:
            print("    no image for " + nasa_id, file=sys.stderr)
            continue
        data = get(src, binary=True)
        with open(os.path.join(PLANET_DIR, body + ".jpg"), "wb") as f:
            f.write(data)
        w, h = jpeg_size(data)
        rows.append({
            "body": body,
            "file": "app/src/main/assets/planets/%s.jpg" % body,
            "nasa_id": nasa_id,
            "source": src,
            "page": "https://images.nasa.gov/details/" + nasa_id,
            "kb": len(data) // 1024,
            "dim": "%dx%d" % (w, h),
            "caption": caption,
        })
        print("    %s  %dx%d  %dKB" % (nasa_id, w, h, len(data) // 1024))
    return rows


# The journey ends in Tokyo, so the Earth it flies towards has to be the
# side with Japan on it. The library full-disk Earths are all the Americas
# or Africa; DSCOVR sits at the Earth-Sun L1 point and photographs the whole
# sunlit disc every couple of hours, so one frame per day is centred on the
# Pacific. This is the frame nearest 140 east.
EPIC_IMAGE = "epic_1b_20260928024318"
EPIC_URL = ("https://epic.gsfc.nasa.gov/archive/natural/2026/09/28/png/"
            + EPIC_IMAGE + ".png")
EPIC_PAGE = "https://epic.gsfc.nasa.gov/"


def fetch_earth_east():
    """The Japan-side Earth, cropped to its disc and lifted a little.

    EPIC natural-colour frames are faithful rather than flattering: the
    disc sits in a wide black frame and the whole thing is dim next to the
    Blue Marble composites. Cropping to the limb and a modest lift in
    brightness, contrast and saturation is all that is done to it.
    """
    from PIL import Image, ImageEnhance  # only this step needs Pillow

    print("  earth_east (DSCOVR/EPIC)")
    blob = get(EPIC_URL, binary=True)
    image = Image.open(io.BytesIO(blob)).convert("RGB")

    # The disc never fills the frame, so find it rather than assume it.
    box = image.convert("L").point(lambda v: 255 if v > 18 else 0).getbbox()
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    half = max(box[2] - box[0], box[3] - box[1]) * 1.03 / 2
    disc = image.crop((int(cx - half), int(cy - half), int(cx + half), int(cy + half)))
    disc = disc.resize((1024, 1024), Image.LANCZOS)
    disc = ImageEnhance.Brightness(disc).enhance(1.12)
    disc = ImageEnhance.Contrast(disc).enhance(1.20)
    disc = ImageEnhance.Color(disc).enhance(1.22)

    os.makedirs(PLANET_DIR, exist_ok=True)
    path = os.path.join(PLANET_DIR, "earth_east.jpg")
    disc.save(path, "JPEG", quality=90, optimize=True)
    kb = os.path.getsize(path) // 1024
    print("    %s  1024x1024  %dKB" % (EPIC_IMAGE, kb))
    return {
        "body": "earth (eastern hemisphere)",
        "file": "app/src/main/assets/planets/earth_east.jpg",
        "nasa_id": EPIC_IMAGE,
        "source": EPIC_URL,
        "page": EPIC_PAGE,
        "kb": kb,
        "dim": "1024x1024",
        "caption": "DSCOVR EPIC full disc centred near 135 east, cropped and lifted",
    }


def fetch_audio():
    os.makedirs(AUDIO_DIR, exist_ok=True)
    print("  downloading CC0 pack")
    blob = get(SFX_ZIP_URL, binary=True)
    rows = []
    with zipfile.ZipFile(io.BytesIO(blob)) as z:
        for cue in CUES:
            member, note = CUES[cue]
            out = os.path.join(AUDIO_DIR, cue + ".ogg")
            with z.open(member) as src, open(out, "wb") as dst:
                shutil.copyfileobj(src, dst)
            kb = os.path.getsize(out) // 1024
            rows.append({
                "cue": cue,
                "file": "app/src/main/res/raw/%s.ogg" % cue,
                "member": member,
                "note": note,
                "kb": kb,
            })
            print("    %-24s %-12s %dKB" % (cue, member, kb))
    return rows


def write_manifest(planets, audio):
    out = os.path.join(ROOT, "ASSETS.md")
    L = []
    L.append("# Bundled assets\n")
    L.append(
        "Every third-party file shipped inside the APK, with its source and licence.\n"
        "Regenerate with `python tools/fetch_assets.py`.\n"
    )
    if planets:
        L.append("\n## Planet photographs\n")
        L.append(
            "Source: [NASA Image and Video Library](https://images.nasa.gov/). "
            "NASA media are generally **not copyrighted** and may be reused; NASA does "
            "not endorse this app. See the "
            "[NASA media usage guidelines]"
            "(https://www.nasa.gov/nasa-brand-center/images-and-media/).\n"
        )
        L.append("\n| Body | File | NASA ID | Picture | Size |")
        L.append("|---|---|---|---|---|")
        for r in planets:
            L.append(
                "| %s | `%s` | [%s](%s) | %s | %s, %dKB |"
                % (r["body"].title(), r["file"], r["nasa_id"], r["page"],
                   r["caption"], r["dim"], r["kb"])
            )
    if audio:
        L.append("\n## Sound cues\n")
        L.append(
            "Source: [60 CC0 Sci-Fi SFX](%s) by rubberduck, released under **CC0 1.0** "
            "(public domain dedication). No attribution is required; it is recorded "
            "here anyway.\n" % SFX_PAGE
        )
        L.append("\n| Cue | File | Original | Role |")
        L.append("|---|---|---|---|")
        for r in audio:
            L.append("| `%s` | `%s` | `%s` | %s |"
                     % (r["cue"], r["file"], r["member"], r["note"]))
        L.append("\nTotal audio: %dKB\n" % sum(r["kb"] for r in audio))
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")
    print("\nwrote " + out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--skip-audio", action="store_true")
    ap.add_argument("--skip-planets", action="store_true")
    ap.add_argument("--skip-earth", action="store_true")
    args = ap.parse_args()

    planets = []
    audio = []
    if not args.skip_planets:
        print("planets:")
        planets = fetch_planets()
    if not args.skip_earth:
        print("earth, Japan side:")
        planets.append(fetch_earth_east())
    if not args.skip_audio:
        print("audio:")
        audio = fetch_audio()
    write_manifest(planets, audio)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
