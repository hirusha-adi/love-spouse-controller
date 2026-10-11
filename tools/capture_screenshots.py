#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Hirusha Adikari
# SPDX-License-Identifier: MIT
"""Capture the English app on an isolated Android 13+ emulator.

Usage: python3 tools/capture_screenshots.py --serial emulator-5556
Install app-debug.apk first. Only emulator serials are accepted because display,
theme, and language settings are changed. Bluetooth commands use the virtual radio.
"""

import argparse
from pathlib import Path
import re
import subprocess
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
    if not args.serial.startswith("emulator-"):
        parser.error("Use an isolated emulator; this tool does not change a personal phone")

    def adb(*command, binary=False):
        result = subprocess.check_output(["adb", "-s", args.serial, *command])
        return result if binary else result.decode().strip()

    def launch(dark=True):
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--locales", LOCALE)
        adb("shell", "cmd", "uimode", "night", "yes" if dark else "no")
        adb("shell", "am", "start", "-W", "-n", ACTIVITY)
        time.sleep(1)

    def nodes():
        adb("shell", "uiautomator", "dump", "/sdcard/lscontroller-ui.xml")
        root = ET.fromstring(adb("shell", "cat", "/sdcard/lscontroller-ui.xml"))
        return [node for node in root.iter("node") if node.attrib.get("package") == PACKAGE]

    def tap(predicate):
        node = next(node for node in nodes() if predicate(node.attrib))
        left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        time.sleep(1)

    def screenshot(directory, number):
        assert nodes(), "App is not visible"
        directory.mkdir(parents=True, exist_ok=True)
        (directory / f"{number}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
        print(f"Captured {directory.relative_to(ROOT)}/{number}.png", flush=True)

    adb("shell", "pm", "grant", PACKAGE, "android.permission.BLUETOOTH_ADVERTISE")
    for setting in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb("shell", "settings", "put", "global", setting, "0")
    adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
    for extras in (("command", "enter"), ("command", "clock", "hhmm", "1010"),
                   ("command", "notifications", "visible", "false"),
                   ("command", "battery", "level", "100", "plugged", "false")):
        command = ["shell", "am", "broadcast", "-a", "com.android.systemui.demo"]
        for index in range(0, len(extras), 2):
            command += ["-e", extras[index], extras[index + 1]]
        adb(*command)

    screens = [("phoneScreenshots", "1080x1920", "420"),
               ("sevenInchScreenshots", "800x1280", "213"),
               ("tenInchScreenshots", "1200x1920", "226")]
    try:
        for kind, dimensions, density in screens:
            adb("shell", "wm", "size", dimensions)
            adb("shell", "wm", "density", density)
            directory = ROOT / "fastlane/metadata/android" / LOCALE / "images" / kind
            launch()
            screenshot(directory, 1)
            if kind == "phoneScreenshots":
                label = "Mode 5"
                tap(lambda attributes: attributes.get("content-desc") == label)
                assert any(node.attrib.get("text") == label for node in nodes()), "Mode 5 did not activate"
                screenshot(directory, 2)
                tap(lambda attributes: attributes.get("content-desc") == label)
            tap(lambda attributes: attributes.get("class") == "android.widget.EditText")
            screenshot(directory, 3 if kind == "phoneScreenshots" else 2)
            adb("shell", "input", "keyevent", "4")
            if kind == "phoneScreenshots":
                launch(dark=False)
                screenshot(directory, 4)
    finally:
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--locales", LOCALE)
        adb("shell", "wm", "size", "1080x1920")
        adb("shell", "wm", "density", "420")
        adb("shell", "cmd", "uimode", "night", "no")
        adb("shell", "rm", "-f", "/sdcard/lscontroller-ui.xml")


if __name__ == "__main__":
    main()
