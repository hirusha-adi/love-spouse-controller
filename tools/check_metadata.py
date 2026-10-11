#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Hirusha Adikari
# SPDX-License-Identifier: MIT
"""Check English listing limits, graphics, and release versioning."""

import hashlib
from pathlib import Path
import re
import struct
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []


def check(condition, message):
    if not condition:
        errors.append(message)


def png_size(path):
    if not path.is_file():
        errors.append(f"Missing graphic: {path.relative_to(ROOT)}")
        return None
    data = path.read_bytes()
    check(data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {path}")
    return struct.unpack(">II", data[16:24]) if len(data) >= 24 else None


gradle = (ROOT / "app/build.gradle.kts").read_text()
version_code = re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1)
version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
recipe = (ROOT / "fdroid/metadata/dev.hirusha.lscontroller.yml").read_text()
check(f"versionName: '{version_name}'" in recipe, "Recipe version name differs from Gradle")
check(f"versionCode: {version_code}" in recipe, "Recipe version code differs from Gradle")
check(f"commit: v{version_name}" in recipe, "Recipe release tag differs from Gradle")
check(f"CurrentVersion: '{version_name}'" in recipe, "CurrentVersion differs from Gradle")
check(f"CurrentVersionCode: {version_code}" in recipe, "CurrentVersionCode differs from Gradle")
license_text = (ROOT / "LICENSE").read_text()
check("MIT License" in license_text and "License: MIT" in recipe, "License and recipe must agree")

listing_root = ROOT / "fastlane/metadata/android"
check(sorted(path.name for path in listing_root.iterdir() if path.is_dir()) == ["en-US"],
      "Store metadata must contain only the English listing")
for locale in ("en-US",):
    listing = listing_root / locale
    for filename, limit in (("title.txt", 50), ("short_description.txt", 80),
                            ("full_description.txt", 4000), (f"changelogs/{version_code}.txt", 500)):
        path = listing / filename
        check(path.is_file(), f"Missing {locale}/{filename}")
        if not path.is_file():
            continue
        value = path.read_text().strip()
        check(0 < len(value) <= limit, f"{locale}/{filename}: {len(value)} characters; limit {limit}")
        if filename == "short_description.txt":
            check(not value.endswith("."), f"{locale}: summary must not end with a period")
    check(png_size(listing / "images/icon.png") == (512, 512), f"{locale}: icon must be 512x512")
    check(png_size(listing / "images/featureGraphic.png") == (1024, 500),
          f"{locale}: feature graphic must be 1024x500")
    screenshots = sorted((listing / "images/phoneScreenshots").glob("*.png"))
    check(len(screenshots) >= 2, f"{locale}: add at least two actual phone screenshots")
    for screenshot in screenshots:
        size = png_size(screenshot)
        check(size is not None and min(size) >= 320 and max(size) <= 3840,
              f"Screenshot dimensions outside 320–3840 pixels: {screenshot.name}")

android = "{http://schemas.android.com/apk/res/android}"
locale_config = ET.parse(ROOT / "app/src/main/res/xml/locales_config.xml").getroot()
check([locale.attrib[android + "name"] for locale in locale_config] == ["en-US"],
      "The app must advertise only English")
manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
permissions = {element.attrib[android + "name"] for element in manifest.findall("uses-permission")}
check(permissions == {"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN",
                      "android.permission.BLUETOOTH_ADVERTISE"}, "Unexpected app permission")
wrapper = ROOT / "gradle/wrapper/gradle-wrapper.jar"
check(hashlib.sha256(wrapper.read_bytes()).hexdigest() ==
      "cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8",
      "Gradle wrapper differs from the published Gradle 8.7 wrapper")
check((ROOT / "app/gradle.lockfile").is_file(), "Missing dependency lockfile")
check((ROOT / "gradle/verification-metadata.xml").is_file(), "Missing dependency checksums")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print(f"F-Droid upstream metadata checks passed for {version_name} ({version_code})")
