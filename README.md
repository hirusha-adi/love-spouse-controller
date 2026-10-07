# Love Spouse Controller

An Android app for controlling BLE-based intimate devices that are compatible with the Love Spouse ecosystem. This includes a wide range of white-label and generic adult toys sold under various brand names that all use the same underlying BLE advertising protocol.

## Compatible Devices

This app works with devices that use the Love Spouse / Weibu IoT BLE 2.4GHz advertising protocol. These are commonly sold on Amazon, AliExpress, and other marketplaces under many different brand names, but they all share the same app and communication protocol. If your device pairs with the "Love Spouse" app on the Play Store / App Store, it's compatible.

The app comes pre-loaded with all known device prefixes, covering the entire device catalog.

## Features

- **Vibration control** - 9 vibration modes
- **Heating control** - Toggle heating element on/off (on supported devices)
- **Suction control** - 5 suction intensity levels (on supported devices)
- **Prefix cycling** - Automatically cycles through all known device prefixes, or target a specific one
- **Material You** - Dynamic color theming on Android 12+

## Install

### Pre-built APK

Download `ls-controller-debug.apk` from this repo and install it on your phone:

```bash
adb install ls-controller-debug.apk
```

Or transfer the APK to your phone and install it manually. You'll need "Install from unknown sources" enabled.

### Build from source

1. Open the project in Android Studio
2. Sync Gradle
3. Run on your device

Or from the command line:

```bash
# Set your Android SDK path
echo "sdk.dir=/path/to/your/android/sdk" > local.properties

# Build
./gradlew assembleDebug

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

1. Open the app and grant the Bluetooth permission when prompted
2. Select a prefix from the dropdown:
   - **All (cycle)** - Rotates through all known prefixes at 300ms each. Use this if you don't know which prefix your device uses. Works with virtually every compatible device.
   - **A specific prefix** - If you know your device's prefix, select it for faster response.
3. Tap a mode button to start broadcasting that command
4. Tap the same button again to stop, or use the buttons at the bottom:
   - **STOP DEVICE** - Sends the stop command (0x00) to the device
   - **Stop Broadcasting** - Silently stops the BLE radio without sending any command

## How It Works

The app uses Android's BLE advertising API to broadcast encoded command packets. Compatible devices passively listen for these packets and respond when they detect a matching prefix in the manufacturer data field. No BLE connection or pairing is required.

## Requirements

- Android 8.0+ (API 26)
- Device with BLE advertising support (most modern Android phones)
- Bluetooth permission granted

## Permissions

- `BLUETOOTH_ADVERTISE` - Required to broadcast BLE advertising packets

## Disclaimer

This app is provided for educational and personal use only. Only use it with devices you own.
