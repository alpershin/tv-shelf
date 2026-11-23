# Build from source

## Requirements

- JDK 17 or newer
- Android SDK Platform 35 and Build Tools 35.0.0
- Android SDK Platform Tools for TV installation
- Bash and zip

Set `ANDROID_SDK_ROOT` or `ANDROID_HOME` to the Android SDK directory. The build script also checks the default Android Studio SDK location on macOS.

```bash
./build.sh
adb install -r build/tv-shelf.apk
```

The build creates a local signing key on first use. Reuse the same key to install future versions over the existing app. To use a different PKCS#12 keystore with alias `local`, set `TV_SHELF_KEYSTORE` and `TV_SHELF_KEYSTORE_PASSWORD`. Keep the keystore out of version control. GitHub Actions artifacts use an ephemeral signing key. Published Releases use a separate persistent key.

## Device checks

- Open **Полка** from the TV’s app list and check the icon and banner.
- Navigate the app shelf, reorder a tile, and reopen the launcher to verify the saved order.
- Open the apps grid and check that it shows TV app icons.
- Open Wi-Fi settings or another app and return with Home. Record the TV model, Android version, and which Home configuration was active.
- Switch the system theme and verify text, focus indicators, and shortcut state colors in both modes.
