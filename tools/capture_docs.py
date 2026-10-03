#!/usr/bin/env python3
"""Drive the app on a connected device and capture the README media.

Takes still screenshots of each screen and builds animated GIFs of the
timed sequences by bursting screencaps and assembling them with Pillow.
(There is no ffmpeg on this machine, so screenrecord's MP4 cannot be
transcoded; bursting is the workable route.)

Buttons are found by their accessibility label rather than by pixel
coordinates. The toolbar has gained buttons twice and changed its touch
target once, and each time the hard-coded taps silently started pressing
whatever had moved into their place.

The sky mode and its meteor need the emulator's virtual compass pointed
at a planet, which is a different job: see capture_sky.py.

Usage:  python tools/capture_docs.py [serial]
"""
from __future__ import annotations

import io
import os
import re
import subprocess
import sys
import time

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
ADB = r"C:/Users/aleks/AppData/Local/Android/Sdk/platform-tools/adb.exe"
SERIAL = sys.argv[1] if len(sys.argv) > 1 else "emulator-5554"
PKG = "com.pebbledetective"

# The two taps that have no label to find: the pebble on the camera
# preview, and the first row of the logbook.
TAP_PEBBLE = (540, 1150)
TAP_FIRST_ENTRY = (540, 570)

NODE = re.compile(r"<node[^>]*>")
DESC = re.compile(r'content-desc="([^"]*)"')
TEXT = re.compile(r' text="([^"]*)"')
BOUNDS = re.compile(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def adb(*args: str) -> str:
    return subprocess.run(
        [ADB, "-s", SERIAL, *args], capture_output=True, text=True, timeout=120
    ).stdout


def raw(*args: str) -> bytes:
    return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, timeout=120).stdout


def shot() -> Image.Image:
    """One frame, PNG-encoded on the device.

    The raw framebuffer is the faster route on the camera screens, where
    the encoder has a photograph to chew through - see capture_sky.py.
    Everything captured here is a drawn scene on near-black, which
    compresses to almost nothing, so asking the device to encode it beats
    pushing ten megabytes of pixels over the wire by about two to one.
    """
    return Image.open(io.BytesIO(raw("exec-out", "screencap", "-p"))).convert("RGB")


def nodes() -> list[tuple[str, str, tuple[int, int]]]:
    """Everything on screen, as (description, text, centre)."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = raw("exec-out", "cat", "/sdcard/ui.xml").decode("utf-8", "replace")
    found = []
    for match in NODE.finditer(xml):
        tag = match.group(0)
        box = BOUNDS.search(tag)
        if not box:
            continue
        x1, y1, x2, y2 = (int(v) for v in box.groups())
        desc = DESC.search(tag)
        text = TEXT.search(tag)
        found.append((
            desc.group(1) if desc else "",
            text.group(1) if text else "",
            ((x1 + x2) // 2, (y1 + y2) // 2),
        ))
    return found


def find(desc: str = "", text: str = "") -> tuple[int, int]:
    for node_desc, node_text, centre in nodes():
        if desc and node_desc == desc:
            return centre
        if text and node_text == text:
            return centre
    raise LookupError("nothing on screen with desc=%r text=%r" % (desc, text))


def tap(point: tuple[int, int]) -> None:
    adb("shell", "input", "tap", str(point[0]), str(point[1]))


def press(desc: str = "", text: str = "", settle: float = 2.5) -> None:
    tap(find(desc=desc, text=text))
    time.sleep(settle)


def save(image: Image.Image, name: str, width: int = 420) -> None:
    out = image.copy()
    out.thumbnail((width, width * 4), Image.LANCZOS)
    path = os.path.join(DOCS, name)
    out.save(path, "PNG", optimize=True)
    print("  %s  %d kB" % (name, os.path.getsize(path) // 1024))


def burst(
    seconds: float,
    name: str,
    width: int = 300,
    frame_ms: int = 220,
    stride: int = 1,
) -> None:
    """Grab frames as fast as screencap allows, then write them as a GIF.

    A long sequence keeps only every `stride`-th frame, with the frame
    duration scaled to match, so the GIF still runs at wall-clock speed
    without the file growing past what a README should carry.
    """
    frames: list[Image.Image] = []
    started = time.time()
    end = started + seconds
    while time.time() < end:
        frames.append(shot())
    # screencap manages only a few frames a second here, and how few
    # varies, so the playback rate is measured rather than assumed. A
    # fixed frame time makes a slow capture play back comically fast.
    captured_ms = int((time.time() - started) * 1000)
    small = []
    for frame in frames[::stride]:
        scaled = frame.copy()
        scaled.thumbnail((width, width * 4), Image.LANCZOS)
        small.append(scaled.convert("P", palette=Image.ADAPTIVE, colors=128))
    path = os.path.join(DOCS, name)
    small[0].save(
        path, save_all=True, append_images=small[1:],
        duration=max(frame_ms, captured_ms // max(1, len(small))),
        loop=0, optimize=True,
    )
    print("  %s  %d frames  %d kB" % (name, len(small), os.path.getsize(path) // 1024))


def restart(wait: float = 11.0) -> None:
    """Relaunch and sit through the titles onto the sky, where it lands."""
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", "%s/.MainActivity" % PKG)
    time.sleep(wait)


def main() -> int:
    os.makedirs(DOCS, exist_ok=True)
    print("capturing:")

    # 0. The title sequence, caught once it has assembled itself. The wait
    # covers the system splash as well, which on the emulator costs the
    # best part of two seconds before Compose draws its first frame.
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", "%s/.MainActivity" % PKG)
    time.sleep(5.6)
    save(shot(), "screen-splash.png")

    # 1. Camera with the reticle. The app lands on the sky, so Detection is
    # one tap along the toolbar rather than where it starts.
    restart()
    press(desc="Detection mode", settle=4)
    save(shot(), "screen-capture.png")

    # 2. Tap to target, then the Deep Research prompt over the frozen still.
    tap(TAP_PEBBLE)
    time.sleep(4)
    save(shot(), "screen-prompt.png")

    # 3. The five second research sequence, as a GIF.
    press(text="Yes, investigate!", settle=0)
    burst(5.5, "research.gif")
    time.sleep(2)
    save(shot(), "screen-result.png")

    # 4. The whole flight, as a GIF, and the landing it ends on.
    press(text="Watch it fly home", settle=0)
    burst(20.5, "journey.gif", stride=2)
    time.sleep(1)
    save(shot(), "screen-landing.png")

    # 4b. Two stills from the flight itself: the cruise, where both worlds
    # are photographs, and the descent over Japan. The GIF passes through
    # both too quickly to show them.
    press(text="Done")
    press(text="Watch it fly home", settle=6.5)
    save(shot(), "screen-cruise.png")
    time.sleep(6.5)
    save(shot(), "screen-descent.png")
    # Done only appears once it has landed, so skip the rest of the fall.
    press(text="Skip", settle=2)
    press(text="Done", settle=4)

    # 5. The logbook, and one entry opened.
    press(desc="Pebble logbook", settle=3)
    save(shot(), "screen-logbook.png")
    tap(TAP_FIRST_ENTRY)
    time.sleep(3)
    save(shot(), "screen-detail.png")
    press(text="Go back", settle=2)

    # 6. The same logbook in Japanese, to show the language switch. Two
    # presses of the globe: English, Russian, Japanese.
    globe = find(desc="Change language")
    tap(globe)
    time.sleep(1.5)
    tap(globe)
    time.sleep(2)
    save(shot(), "screen-logbook-ja.png")
    tap(globe)  # back to English
    time.sleep(2)

    # 7. Radar, which wants the camera behind it, so start from the sky.
    restart()
    press(desc="Radar mode", settle=7)
    save(shot(), "screen-radar.png")

    print("done")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
