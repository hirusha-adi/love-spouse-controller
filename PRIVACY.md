# Privacy

LS Controller operates locally and does not request Internet access. It has
no accounts, analytics, advertising SDKs, crash-reporting services, or cloud
connections. It does not collect, save, or send personal information to the
developer. The app does not scan for devices, read location, access contacts,
or read device battery levels.

Android 12 and later require the Nearby devices permission for Bluetooth
advertising. Earlier Android releases use the normal Bluetooth permissions.
No location permission is required. Commands and the selected prefix remain
in memory during the current screen session. Cloud backup and device transfer
of application data are disabled.

Bluetooth command broadcasts can be received by nearby compatible devices.
They are not an authenticated or individually paired connection. Cycling all
prefixes may control more than one device. The broadcast does not include the
phone's name, and the application cannot verify who received a command.

Android and device firmware may independently retain system diagnostics.
This app does not upload diagnostics. Downloading the source, installing from
a repository, or visiting GitHub is subject to those services' privacy policies.

Report app issues at
[GitHub](https://github.com/hirusha-adi/love-spouse-controller/issues).
