# Release procedure

Release 2.1 uses version code 3 and tag `v2.1`. Earlier tags do not match their
Gradle version; do not move or reuse them. Debug builds use a separate application
ID. Release distributors build the same source and ID without a special flavor.

## Prepare the source release

1. Confirm Gradle versions, the F-Droid recipe, and the release tag agree.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, under 500 characters. Replace “unreleased” in `CHANGELOG.md` with the actual release date.
3. Refresh screenshots when the interface changes. Run `tools/render_artwork.sh` when its SVG sources change; it requires librsvg and DejaVu Sans fonts.
4. Run the metadata checker, release build, Android lint, `reuse lint`, and reproducibility check. Test commands on compatible hardware you own.
5. Commit the release files, then create and publish the source tag after reviewing the changes.

```bash
git tag -a v2.1 -m "Release 2.1"
git push origin main
git push origin v2.1
```

Build published APKs from a clean checkout of that exact tag, using OpenJDK 17,
platform 35, Build Tools 34.0.0, and the pinned Gradle and dependencies. Do not
embed dates, machine paths, or signing details. Leave AGP's VCS metadata enabled.

```bash
./gradlew --no-daemon --no-build-cache --dependency-verification strict clean assembleRelease lintRelease
```

The normal F-Droid flow builds and signs the unsigned release APK using its
own key. Debug APKs cannot update an F-Droid-signed installation.

## Optional developer signatures

Developer-signed distribution needs a permanent release key owned by you.
This repository does not generate a temporary key or store private keys or
signing secrets. Keep the key and backup outside the repository.

Align and sign using Build Tools 34.0.0, which F-Droid documents as compatible
with its APK signature-copying verification:

```bash
"$ANDROID_HOME/build-tools/34.0.0/zipalign" -p -f 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk /tmp/ls-controller-aligned.apk
"$ANDROID_HOME/build-tools/34.0.0/apksigner" sign \
  --ks /path/outside/repository/release.jks \
  --out ls-controller-2.1.apk /tmp/ls-controller-aligned.apk
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --verbose --print-certs ls-controller-2.1.apk
```

Let apksigner prompt for passwords. Publish `ls-controller-2.1.apk` as an asset
under `v2.1`. Only after the permanent key and signed asset exist, add these
fields to fdroiddata using the actual lower-case SHA-256 certificate fingerprint:

```yaml
Binaries: https://github.com/hirusha-adi/love-spouse-controller/releases/download/v%v/ls-controller-%v.apk
AllowedAPKSigningKeys:
  - <actual-lower-case-sha256-certificate-fingerprint>
```

That placeholder is documentation and is not in the submitted recipe. Have
F-Droid verify signature copying and its independent rebuild before switching
distribution to developer signatures. Locally identical unsigned builds alone
do not prove that a signed release is verified by F-Droid.

## Updating dependencies

Change pinned versions deliberately, then regenerate locks and checksums:

```bash
./gradlew --no-daemon --write-locks --write-verification-metadata sha256 \
  assembleDebug assembleRelease lintRelease
```

Review artifact origins, checksums, and licenses before committing. Bootstrapping
checksums trusts the downloads; it does not independently authenticate them.
Normal builds enforce committed checksums and locks without rewriting them.
