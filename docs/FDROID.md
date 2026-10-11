# F-Droid submission

The repository contains upstream store metadata and a build recipe for release
2.1 (version code 3). Preparing the repository does not publish the app or
guarantee acceptance. F-Droid's maintainers make the inclusion decision.

## Inclusion review

The app has public GitHub source and an MIT license. Gradle wrapper files
remain Apache-2.0. `NOTICE`, `LICENSES/`, SPDX headers, and `REUSE.toml` record
licensing for source, documentation, and artwork. The launcher
and store graphics use original radio/pulse artwork with editable SVG and
Android vector sources.

The Android build uses pinned FLOSS tools and libraries from Google Maven,
Maven Central, and the Gradle plugin portal. There are no proprietary SDKs,
embedded APKs, app-provided executable downloads, API keys, ads, analytics,
online services, or dependency on the proprietary companion app. The standard
Gradle wrapper matches its published checksum; F-Droid removes wrapper files
and uses its own Gradle installation.

The release application ID stays `dev.hirusha.lscontroller`, preserving its
existing identity. F-Droid recommends an ID based on a developer-owned domain.
Verify domain ownership if requested; this preparation does not establish it.

The listing openly describes intimate hardware and its adult audience, without
explicit imagery. F-Droid's policy considers suitability for all users,
including children, so this use case needs a maintainer decision. The current
fdroiddata anti-feature configuration does not contain `NSFW`, although the
metadata reference still describes it. The recipe explains the use case in
`MaintainerNotes` instead of adding an unsupported flag. Another F-Droid-compatible
repository is possible if the main repository declines the app.

Real hardware is still needed to verify commands against supported device
families. A successful build does not establish universal compatibility.

## Store metadata and optional presentation

`fastlane/metadata/android/en-US/` contains an English title, summary,
description, version-code changelogs, a 512×512 icon, a 1024×500 feature graphic,
and actual app screenshots from a Pixel 6a and emulated seven-inch and ten-inch
tablets. The Pixel images show idle controls and the prefix picker in English.
No Fastlane installation or account is needed. Regenerate emulator
screenshots with
`python3 tools/capture_screenshots.py --serial emulator-5556` after installing
the debug APK on an isolated Android 13+ emulator. The tool exercises the
virtual Bluetooth radio and captures real UI states, without physical hardware.

To replace only the phone images with captures from an unlocked Android 13+
phone, install the debug APK, allow its Bluetooth permission, and run
`python3 tools/capture_phone_screenshots.py --serial <device-serial>`. This
captures the idle screen and prefix picker without sending device commands or
changing system display settings. It restores the debug app's language and
leaves the release app's data untouched.

These files provide the English store listing and optional graphics and
screenshots. The recipe omits `Summary` and `Description` so upstream listing
text is imported.

TV/Wear screenshots, a promotional video, and donation accounts are optional
fields for applicable projects. TV/Wear builds, a video, and verified donation
destinations do not exist for this app; no placeholders or invented URLs are
supplied. Additional tested screen sizes are welcome.

## Checks

```bash
./gradlew --no-daemon --dependency-verification strict assembleDebug assembleRelease lintRelease
python3 tools/check_metadata.py
python3 tools/check_reproducibility.py
python3 tools/check_fdroid.py
reuse lint
```

`reuse` and `fdroidserver` are optional development tools from Debian or PyPI.
The recipe checker uses fdroidserver and current official category/anti-feature
definitions, fetched into a temporary directory. The reproducibility
script builds two separate source snapshots without build-cache reuse, compares
unsigned APK bytes, and writes `build/reproducibility/report.json`. Passing
checks the local toolchain; F-Droid's independent rebuild and signature checks
still need to run in its infrastructure. GitHub CI runs metadata, release, lint,
and reproducibility checks and uploads unsigned artifacts without publishing.

## Submit

Follow [RELEASING.md](RELEASING.md), then publish the changes and matching `v2.1`
source tag. The recipe needs that tag. Older tags lack the license and metadata.

Fork and clone [fdroiddata](https://gitlab.com/fdroid/fdroiddata). Copy
`fdroid/metadata/dev.hirusha.lscontroller.yml` into its `metadata/` directory.
Within that checkout, run:

```bash
fdroid rewritemeta dev.hirusha.lscontroller
fdroid lint -f dev.hirusha.lscontroller
fdroid checkupdates dev.hirusha.lscontroller
fdroid build --test dev.hirusha.lscontroller:3
```

Use F-Droid's supported environment for the final build. Open a metadata merge
request with the purpose, license, tag, results, and adult-device disclosure.
As upstream author, state that you support inclusion and intend to maintain
the app. Alternatively, use the
[packaging tracker](https://gitlab.com/fdroid/rfp/-/issues).

Automatic updates use stable `v<versionName>` tags and explicitly extract the
Kotlin DSL version fields. Increase `versionCode` for every release and add
its corresponding changelog in the English listing.

## Official references

- [Inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/)
- [Submission quick start](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [Descriptions, graphics, and screenshots](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/)
- [Build metadata reference](https://f-droid.org/en/docs/Build_Metadata_Reference/)
- [Reproducible builds](https://f-droid.org/en/docs/Reproducible_Builds/)
- [Current anti-feature definitions](https://gitlab.com/fdroid/fdroiddata/-/blob/master/config/antiFeatures.yml)
- [Bluetooth brand guide](https://www.bluetooth.com/wp-content/uploads/2023/08/BTLA_w_Brand_Guide.pdf)
