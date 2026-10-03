#!/usr/bin/env python3
"""Drive the app on a connected device and capture the README media.

Takes still screenshots of each screen and builds animated GIFs of the two
timed sequences by bursting screencaps and assembling them with Pillow.
(There is no ffmpeg on this machine, so screenrecord's MP4 cannot be
transcoded; bursting is the workable route.)

Usage:  python tools/capture_docs.py [serial]
"""
from __future__ import annotations

import io
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

# Screen is 1080x2400; taps are in device pixels.
TAP_CAPTURE = (540, 1150)
TAP_YES = (539, 1226)
TAP_JOURNEY = (539, 1842)
TAP_HISTORY = (985, 212)
# The top controls are right-aligned, so the globe sits further right on
# screens that have no logbook button of their own.
TAP_GLOBE = (691, 212)
TAP_GLOBE_NO_HISTORY = (839, 212)


def adb(*args: str) -> str:
    return subprocess.run(
        [ADB, "-s", SERIAL, *args], capture_output=True, text=True, timeout=120
    ).stdout


def shot() -> Image.Image:
    raw = subprocess.run(
        [ADB, "-s", SERIAL, "exec-out", "screencap", "-p"],
        capture_output=True, timeout=60,
    ).stdout
    return Image.open(io.BytesIO(raw)).convert("RGB")


def tap(point: tuple[int, int]) -> None:
    adb("shell", "input", "tap", str(point[0]), str(point[1]))


def save(image: Image.Image, name: str, width: int = 420) -> None:
    out = image.copy()
    out.thumbnail((width, width * 4), Image.LANCZOS)
    path = os.path.join(DOCS, name)
    out.save(path, "PNG", optimize=True)
    print(f"  {name}  {os.path.getsize(path)//1024} kB")


def burst(seconds: float, name: str, width: int = 300, frame_ms: int = 220) -> None:
    """Grab frames as fast as screencap allows, then write them as a GIF."""
    frames: list[Image.Image] = []
    end = time.time() + seconds
    while time.time() < end:
        frames.append(shot())
    small = []
    for f in frames:
        g = f.copy()
        g.thumbnail((width, width * 4), Image.LANCZOS)
        small.append(g.convert("P", palette=Image.ADAPTIVE, colors=128))
    path = os.path.join(DOCS, name)
    small[0].save(
        path, save_all=True, append_images=small[1:],
        duration=frame_ms, loop=0, optimize=True,
    )
    print(f"  {name}  {len(small)} frames  {os.path.getsize(path)//1024} kB")


def restart() -> None:
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(5)


def main() -> int:
    os.makedirs(DOCS, exist_ok=True)
    print("capturing:")

    # 1. Camera with the reticle.
    restart()
    time.sleep(2)
    save(shot(), "screen-capture.png")

    # 2. Tap to capture, then the Deep Research prompt over the frozen still.
    tap(TAP_CAPTURE)
    time.sleep(4)
    save(shot(), "screen-prompt.png")

    # 3. The five second research sequence, as a GIF.
    tap(TAP_YES)
    burst(5.5, "research.gif")

    time.sleep(2)
    save(shot(), "screen-result.png")

    # 4. The journey, as a GIF.
    tap(TAP_JOURNEY)
    burst(12.5, "journey.gif")
    time.sleep(1)

    # 5. The logbook.
    restart()
    tap(TAP_HISTORY)
    time.sleep(3)
    save(shot(), "screen-logbook.png")

    # 6. The same screen in Japanese, to show the language switch.
    tap(TAP_GLOBE_NO_HISTORY)
    time.sleep(1)
    tap(TAP_GLOBE_NO_HISTORY)
    time.sleep(2)
    save(shot(), "screen-logbook-ja.png")
    tap(TAP_GLOBE_NO_HISTORY)  # back to English

    print("done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
