#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Hirusha Adikari
# SPDX-License-Identifier: MIT
set -euo pipefail
cd "$(dirname "$0")/.."
output_dir="fastlane/metadata/android/en-US/images"
mkdir -p "$output_dir"
rsvg-convert -o "$output_dir/icon.png" artwork/icon.svg
rsvg-convert -o "$output_dir/featureGraphic.png" artwork/featureGraphic.svg
