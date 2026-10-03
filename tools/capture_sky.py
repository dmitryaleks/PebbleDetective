#!/usr/bin/env python3
"""Capture the Planets-around screenshots on the emulator.

Kept apart from capture_docs.py because this one has to fly the virtual
phone: it seeds a location, points the emulated compass and accelerometer
at a chosen bearing, and taps the planet it knows will be there. None of
that applies on real hardware, where you simply hold the phone up.

Usage:  python tools/capture_sky.py [serial]
"""
from __future__ import annotations

import io
import math
import os
import subprocess
import sys
import time

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
ADB = r"C:/Users/aleks/AppData/Local/Android/Sdk/platform-tools/adb.exe"
SERIAL = sys.argv[1] if len(sys.argv) > 1 else "emulator-5554"
PKG = "com.pebbledetective"

# Koto, Tokyo: the same spot the journey animation lands on.
LATITUDE, LONGITUDE = 35.672, 139.817

# Tokyo field: about 46 microtesla dipping 49 degrees below horizontal.
FIELD = 46.0
INCLINATION = 49.0

TAP_SKY = (691, 212)


def adb(*args: str) -> str:
    return subprocess.run(
        [ADB, "-s", SERIAL, *args], capture_output=True, text=True, timeout=120
    ).stdout


def shot() -> Image.Image:
    raw = subprocess.run(
        [ADB, "-s", SERIAL, "exec-out", "screencap", "-p"], capture_output=True, timeout=60
    ).stdout
    return Image.open(io.BytesIO(raw)).convert("RGB")


def save(image: Image.Image, name: str, width: int = 420) -> None:
    out = image.copy()
    out.thumbnail((width, width * 4), Image.LANCZOS)
    path = os.path.join(DOCS, name)
    out.save(path, "PNG", optimize=True)
    print(f"  {name}  {os.path.getsize(path)//1024} kB")


def aim(azimuth_deg: float, pitch_deg: float) -> None:
    """Point the virtual phone, held upright, at a bearing and elevation.

    The emulator has no rotation vector of its own; Android fuses one out
    of gravity and the magnetic field, so those are what get set. With the
    camera looking along the bearing, screen-up is the device y axis and
    screen-right is x, which gives the two vectors below.
    """
    a = math.radians(azimuth_deg)
    p = math.radians(pitch_deg)
    north = FIELD * math.cos(math.radians(INCLINATION))
    down = -FIELD * math.sin(math.radians(INCLINATION))

    gx, gy, gz = 0.0, 9.81 * math.cos(p), -9.81 * math.sin(p)
    bx = -north * math.sin(a)
    by = -north * math.cos(a) * math.sin(p) + down * math.cos(p)
    bz = -north * math.cos(a) * math.cos(p) - down * math.sin(p)

    adb("emu", "sensor", "set", "acceleration", f"{gx:.3f}:{gy:.3f}:{gz:.3f}")
    adb("emu", "sensor", "set", "magnetic-field", f"{bx:.3f}:{by:.3f}:{bz:.3f}")


def seed_location() -> None:
    """Give the app a place to stand.

    The emulator geo fix is unreliable across reboots, so this writes a
    logbook entry instead and lets the app fall back to it, which is the
    same path a real phone takes indoors with no fix yet.
    """
    script = os.path.join(DOCS, "_seed.sh")
    line = (
        '{"id":"seed-koto","capturedAtEpochMs":1790000000000,'
        '"timeZoneId":"Asia/Tokyo","status":"RESEARCHED","planetId":"mars",'
        '"dominantColourArgb":-5000000,'
        f'"latitude":{LATITUDE},"longitude":{LONGITUDE}}}'
    )
    with io.open(script, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("mkdir -p files\nmkdir -p files/pebbles\n")
        handle.write(f"printf '%s\\n' '{line}' > files/pebbles/index.jsonl\n")
    subprocess.run([ADB, "-s", SERIAL, "push", script, "/data/local/tmp/seed.sh"],
                   capture_output=True, timeout=60)
    adb("shell", "run-as", PKG, "sh", "/data/local/tmp/seed.sh")
    os.remove(script)


def main() -> int:
    os.makedirs(DOCS, exist_ok=True)
    print("capturing:")

    seed_location()
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(6)
    adb("shell", "input", "tap", str(TAP_SKY[0]), str(TAP_SKY[1]))
    time.sleep(4)

    # Saturn is at opposition in early October 2026 and sits in the
    # south-east through the evening, with Neptune a few degrees off it.
    aim(135.0, 48.0)
    time.sleep(3)
    save(shot(), "screen-sky.png")

    # Call one down, then swing after it. It lands within a dozen degrees
    # of the bearing it came from, so following it is a matter of tilting.
    #
    # The emulator fuses its own rotation vector out of the two vectors
    # above and does not track them exactly, so the impact may well come
    # down near an edge of the frame; nudge the bearing here if the shot
    # needs recentring. On a real phone you simply look where it went.
    adb("shell", "input", "tap", "806", "1146")
    time.sleep(0.9)
    aim(135.0, 6.0)
    time.sleep(1.1)
    aim(135.0, -9.0)
    time.sleep(1.6)
    save(shot(), "screen-meteor.png")

    print("done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
