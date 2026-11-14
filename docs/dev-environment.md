# Dev environment

Recorded 2026-11-14 (Task 1).

## Installed on this machine

- **JDK 27.0.1** — Homebrew (`/home/linuxbrew/.linuxbrew/opt/openjdk`). ≥17 required by AGP. Satisfies Task 1 Step 1.
- **Android SDK** — `$ANDROID_HOME=/home/hasan/Android/Sdk`
  - `platforms;android-35` (revision 2)
  - `build-tools;35.0.0`
  - `platform-tools` 37.0.1
  - cmdline-tools at `~/Android/Sdk/cmdline-tools/latest`
  - Licenses accepted.
- **adb** — preinstalled at `~/.local/bin/adb`; SDK platform-tools now also provide it.
- Gradle: not installed system-wide; the project uses the Gradle wrapper.

> `ANDROID_HOME` is not exported by default shell startup. Prefix commands with
> `export ANDROID_HOME=~/Android/Sdk` or rely on the project's
> `local.properties` (written during Task 2) pointing at it.

## Phone

- Device serial: `15010044525445343203be75e2a55b`
- Android 15 (release 15, SDK 35) — matches the spec's target.
- USB debugging: on.
- **Shizuku: installed** (`moe.shizuku.privileged.api`).
- **`com.android.shell` has `android.permission.TETHER_PRIVILEGED: granted=true`** —
  verified 2026-11-14 via `dumpsys package com.android.shell`. This is the gate
  that decides whether the Shizuku privileged toggle can work; it is confirmed
  BEFORE implementation (plan Task 5 Step 3.2 verified early).
