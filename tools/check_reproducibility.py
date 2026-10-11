#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Hirusha Adikari
# SPDX-License-Identifier: MIT
"""Build two independent source snapshots and compare unsigned APKs byte for byte.

This checks the local toolchain, not F-Droid's build server or developer signatures.
GRADLE_BIN can select a distribution's Gradle binary instead of the wrapper.
"""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def git(*args, cwd=ROOT):
    return subprocess.check_output(["git", *args], cwd=cwd)


def main():
    paths = git("ls-files", "--cached", "--others", "--exclude-standard", "-z").decode().split("\0")
    source_files = sorted({path for path in paths if path and (ROOT / path).is_file()})
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk and (ROOT / "local.properties").is_file():
        for line in (ROOT / "local.properties").read_text().splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.removeprefix("sdk.dir=")
    if not sdk or not Path(sdk).is_dir():
        sys.exit("Set ANDROID_HOME to an installed Android SDK, or configure sdk.dir in local.properties")
    output_dir = ROOT / "build/reproducibility"
    output_dir.mkdir(parents=True, exist_ok=True)
    hashes = []
    with tempfile.TemporaryDirectory(prefix="lscontroller-repro-") as temporary:
        for number in (1, 2):
            destination = Path(temporary) / f"source-{number}"
            # Preserve identical Git revision metadata without changing the working repository.
            subprocess.run(["git", "clone", "--quiet", "--no-hardlinks", str(ROOT), str(destination)], check=True)
            for existing in git("ls-files", "-z", cwd=destination).decode().split("\0"):
                if existing and existing not in source_files:
                    (destination / existing).unlink(missing_ok=True)
            for relative in source_files:
                original = ROOT / relative
                if original.is_symlink():
                    sys.exit(f"Source snapshot does not support symlinks: {relative}")
                target = destination / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(original, target)
            (destination / "local.properties").write_text(f"sdk.dir={sdk}\n")
            gradle = os.environ.get("GRADLE_BIN") or str(destination / "gradlew")
            subprocess.run([
                gradle, "--no-daemon", "--no-build-cache", "--no-configuration-cache",
                "--rerun-tasks", "--dependency-verification", "strict", "clean", ":app:assembleRelease"
            ], cwd=destination, check=True)
            apk = destination / "app/build/outputs/apk/release/app-release-unsigned.apk"
            copy = output_dir / f"release-{number}.apk"
            shutil.copy2(apk, copy)
            hashes.append(hashlib.sha256(copy.read_bytes()).hexdigest())
    reproducible = hashes[0] == hashes[1]
    report = {
        "git_revision": git("rev-parse", "HEAD").decode().strip(),
        "working_tree_clean": not git("status", "--porcelain").strip(),
        "sha256": hashes,
        "reproducible": reproducible,
        "scope": "Two independent source paths using the same local SDK, JDK and dependency cache"
    }
    (output_dir / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    if not reproducible:
        with zipfile.ZipFile(output_dir / "release-1.apk") as first, \
                zipfile.ZipFile(output_dir / "release-2.apk") as second:
            first_names, second_names = set(first.namelist()), set(second.namelist())
            changed = sorted((first_names ^ second_names) | {
                name for name in first_names & second_names if first.read(name) != second.read(name)
            })
        print(f"Different APK entries: {changed or 'ZIP metadata only'}", file=sys.stderr)
        sys.exit("Release builds are not byte-for-byte reproducible; see build/reproducibility")
    print(f"Release APKs are byte-for-byte identical: {hashes[0]}")
    print("Report: build/reproducibility/report.json")


if __name__ == "__main__":
    main()
