# USB Tether Tile

A Quick Settings tile that toggles **USB tethering** on your Android phone with
one tap — no digging through Settings. No root required.

![how it works](#) <!-- TODO: add screenshot -->

## What it does

- Adds a **"USB tether"** tile to your Quick Settings panel.
- Tapping it turns USB tethering **on** (if off) or **off** (if on).
- The tile reflects the live state — including toggles made from system Settings.
- If anything goes wrong (no cable, service error), you get a toast telling you
  what happened and a shortcut into the system tethering settings.

## How it works

Toggling USB tethering is a privileged operation: regular apps are not allowed
to do it. This app uses [Shizuku](https://shizuku.rikka.app/) to make the call
with shell-level privileges (the same mechanism `adb shell` uses). It calls the
system's `ITetheringConnector` service directly, so there is no dependency on
any Settings UI being open.

**If Shizuku isn't running**, tapping the tile falls back to opening the
system's USB tethering settings page instead — the tile degrades gracefully.

## Requirements

- Android 13+ (developed and verified on Android 15)
- [Shizuku](https://shizuku.rikka.app/) installed and running for the one-tap
  toggle (otherwise you get the Settings-page fallback)
- No root required

## Install

1. Download the latest APK from
   [Releases](https://github.com/hasanmuammar/usb-tether-tile/releases).
2. Install it (you'll need to allow "install unknown apps" for your browser or
   file manager).
3. Open the app and tap **Grant Shizuku permission** → **Allow all the time**.
   (Make sure Shizuku is running first — see the
   [Shizuku setup guide](https://shizuku.rikka.app/guide/setup/).)
4. Pin the tile: pull down Quick Settings → edit (pencil) → drag **USB tether**
   into your active tiles.
5. Plug in a USB cable to your computer and tap the tile.

## Building from source

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK (see below)
```

Release builds are signed with a keystore described in `keystore.properties`
(gitignored — see `keystore.properties.example`). To build a release yourself:

```bash
cp keystore.properties.example keystore.properties
# edit keystore.properties to point at your keystore
keytool -genkeypair -keystore my-release.jks -keyalg RSA -keysize 4096 \
  -validity 10950 -alias youralias
./gradlew :app:assembleRelease
```

The Gradle daemon needs a JDK 21 (Homebrew's OpenJDK 27 is known to break
Kotlin's version parser); point at yours via `~/.gradle/gradle.properties`:

```
org.gradle.java.home=/path/to/jdk-21
```

## License

[MIT](LICENSE)
