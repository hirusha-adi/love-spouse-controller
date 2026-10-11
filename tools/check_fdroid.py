#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Hirusha Adikari
# SPDX-License-Identifier: MIT
"""Lint the submission recipe with fdroidserver and current official definitions.

Run with a Python environment containing fdroidserver. Only public configuration
files are fetched; no issue, merge request, or release is created.
"""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from urllib.request import urlopen

import yaml

ROOT = Path(__file__).resolve().parents[1]


def main():
    with tempfile.TemporaryDirectory(prefix="lscontroller-fdroid-lint-") as temporary:
        directory = Path(temporary)
        (directory / "metadata").mkdir()
        (directory / "config").mkdir()
        shutil.copy2(ROOT / "fdroid/metadata/dev.hirusha.lscontroller.yml", directory / "metadata")
        for name in ("categories", "antiFeatures"):
            url = f"https://gitlab.com/fdroid/fdroiddata/-/raw/master/config/{name}.yml"
            with urlopen(url, timeout=30) as response:
                data = yaml.safe_load(response.read())
            # The linter copies repository UI icons as a side effect. Validation
            # needs their names/descriptions, not those unrelated binary assets.
            for details in data.values():
                details.pop("icon", None)
            (directory / "config" / f"{name}.yml").write_text(yaml.safe_dump(data, sort_keys=False))
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk and (ROOT / "local.properties").exists():
            for line in (ROOT / "local.properties").read_text().splitlines():
                if line.startswith("sdk.dir="):
                    sdk = line.removeprefix("sdk.dir=")
        if sdk:
            (directory / "config.yml").write_text(yaml.safe_dump({"sdk_path": sdk}))
        subprocess.run([sys.executable, "-m", "fdroidserver", "lint", "-f",
                        "dev.hirusha.lscontroller"], cwd=directory, check=True)
    print("F-Droid recipe lint passed against current fdroiddata definitions")


if __name__ == "__main__":
    main()
