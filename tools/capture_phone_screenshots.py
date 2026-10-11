#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Hirusha Adikari
# SPDX-License-Identifier: MIT
"""Capture idle controls and the prefix picker on an unlocked Android 13+ phone.

Install app-debug.apk and allow its Bluetooth permission first. This tool never
presses command controls or changes system display, theme, or language settings.
It restores the debug app's original language after capturing English screenshots.
"""

import argparse
from pathlib import Path
import re
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.hirusha.lscontroller.debug"
ACTIVITY = f"{PACKAGE}/dev.hirusha.lscontroller.MainActivity"
LOCALE = "en-US"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    args = parser.parse_args()
    remote_xml = f"/sdcard/lscontroller-capture-{time.time_ns()}.xml"

    def adb(*command, binary=False):
        result = subprocess.check_output(["adb", "-s", args.serial, *command])
        return result if binary else result.decode().strip()

    def check_foreground():
        policy = adb("shell", "dumpsys", "window", "policy")
        if re.search(r"\bshowing=true\b", policy):
            raise RuntimeError("Unlock the phone before capturing screenshots")
        windows = adb("shell", "dumpsys", "window")
        focused = re.search(r"mCurrentFocus=(.*)", windows)
        if not focused:
            raise RuntimeError("The debug app must be the foreground window")
        blocks = re.split(r"(?m)^\s*Window #\d+ ", windows)[1:]
        focused_block = next((block for block in blocks
                              if block.startswith(focused.group(1).strip() + ":")), "")
        owner = re.search(r"mOwnerUid=.*\bpackage=(\S+)", focused_block)
        if not owner or owner.group(1) != PACKAGE:
            raise RuntimeError("The debug app must own the foreground window")

    def nodes():
        check_foreground()
        adb("shell", "uiautomator", "dump", remote_xml)
        root = ET.fromstring(adb("shell", "cat", remote_xml))
        app_nodes = [node for node in root.iter("node")
                     if node.attrib.get("package") == PACKAGE]
        if not app_nodes:
            raise RuntimeError("The app is not visible")
        return app_nodes

    locale_output = adb("shell", "cmd", "locale", "get-app-locales", PACKAGE)
    match = re.search(r"\[(.*?)\]", locale_output)
    if not match:
        raise RuntimeError("Could not read the app's original language")
    original_locale = match.group(1)

    try:
        with tempfile.TemporaryDirectory(prefix="lscontroller-phone-") as temporary:
            directory = Path(temporary)
            adb("shell", "am", "force-stop", PACKAGE)
            adb("shell", "cmd", "locale", "set-app-locales", PACKAGE,
                "--locales", LOCALE)
            adb("shell", "am", "start", "-W", "-n", ACTIVITY)
            time.sleep(1)
            app_nodes = nodes()
            if not any(node.attrib.get("text") == "Idle" for node in app_nodes):
                raise RuntimeError("Only the idle screen may be captured on a phone")
            check_foreground()
            (directory / "1.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
            field = next(node for node in app_nodes
                         if node.attrib.get("class") == "android.widget.EditText")
            left, top, right, bottom = map(int, re.findall(r"\d+", field.attrib["bounds"]))
            adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            time.sleep(1)
            if not any(node.attrib.get("text", "").startswith("wbMSE (")
                       for node in nodes()):
                raise RuntimeError("The prefix picker did not open")
            check_foreground()
            (directory / "2.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
            adb("shell", "input", "keyevent", "KEYCODE_BACK")

            destination = ROOT / "fastlane/metadata/android" / LOCALE / "images/phoneScreenshots"
            destination.mkdir(parents=True, exist_ok=True)
            for source in sorted(directory.glob("*.png")):
                (destination / source.name).write_bytes(source.read_bytes())
                print(f"Captured {destination.relative_to(ROOT)}/{source.name}", flush=True)
            for obsolete in destination.glob("*.png"):
                if obsolete.name not in {"1.png", "2.png"}:
                    obsolete.unlink()
    finally:
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "cmd", "locale", "set-app-locales", PACKAGE,
            "--locales", original_locale)
        adb("shell", "rm", "-f", remote_xml)


if __name__ == "__main__":
    main()
