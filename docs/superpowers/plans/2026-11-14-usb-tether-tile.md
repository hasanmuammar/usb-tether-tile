# USB Tether Tile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A quick settings tile on Android 15 that toggles USB tethering via Shizuku, falling back to opening the tethering settings page when Shizuku is unavailable.

**Architecture:** Single-module Kotlin app. A `TileService` renders tile state from public tethering APIs (state observation needs no privileges). Toggling calls the hidden `IConnectivityManager.setUsbTethering(boolean, String)` binder method through Shizuku's `ShizukuBinderWrapper`, reached reflectively with HiddenApiBypass. When Shizuku is missing/dead/ungranted, the tile degrades: ungranted → open MainActivity to request permission; missing/dead → open the system tethering settings screen.

**Tech Stack:** Kotlin, Android Gradle Plugin, Shizuku API 13.1.x (`dev.rikka.shizuku:api`, `dev.rikka.shizuku:provider`), HiddenApiBypass (LSPosed), plain XML views (no Compose), JUnit for pure-logic tests.

**Spec:** `docs/superpowers/specs/2026-11-14-usb-tether-tile-design.md`

## Global Constraints

- minSdk 33, targetSdk 35, compileSdk 35.
- applicationId `dev.hasan.usbtethertile`; app label "USB Tether"; tile label "USB tether".
- USB only — no Wi-Fi hotspot/Bluetooth/Ethernet tethering anywhere in the code.
- Toggle must go through the shell-privileged path: `IConnectivityManager.setUsbTethering(boolean enable, String callerPkg)` via `ShizukuBinderWrapper` around `ServiceManager.getService("connectivity")`. Never call the unprivileged `ConnectivityManager` instance for toggling.
- Tethered USB iface detection matches names starting with `usb`, `ncm`, or `rndis` (Android 14+ uses `ncm0`, older `usb0`/`rndis0`).
- Result code `0` from `setUsbTethering` is success (`TETHER_ERROR_NO_ERROR`); any nonzero is failure.
- Fallback intent: action `android.settings.TETHER_SETTINGS`; if that resolves to nothing, component `com.android.settings/com.android.settings.TetherSettings`; if that also fails, action `android.settings.WIRELESS_SETTINGS`.
- No network permission in the manifest; no tracking, no analytics.

## Review Focus

Failure modes a person using this tile would hit, and the test that pins each:

1. **USB cable not connected when enabling** — the privileged call returns a nonzero error; expected: toast "Connect a USB cable" and tile stays INACTIVE, no crash. Pins: Task 5 manual step.
2. **Shizuku installed but process not running** (reboot without auto-start) — `Shizuku.pingBinder()` false; expected: tap opens tethering settings, tile still shows real tether state. Pins: Task 3 unit test (status mapping), Task 6 manual step.
3. **Shizuku running but permission not yet granted** — expected: tap opens MainActivity which triggers the Shizuku grant dialog, not the settings fallback. Pins: Task 3 unit test (status mapping), Task 6 manual step.
4. **Tethered iface naming variance** (`ncm0` vs `usb0` vs `rndis0`) — expected: all three report USB-tethered. Pins: Task 3 unit tests.
5. **Tether toggled from system Settings while tile is listening** — expected: tile state updates via `TetheringEventCallback`, not stale. Pins: Task 5 manual step.

---

### Task 1: Build environment (JDK + Android SDK + adb check)

**Files:** none created in repo (machine setup only); notes go in `docs/dev-environment.md`.

**Interfaces:**
- Produces: `java`, `gradle` wrapper, `$ANDROID_HOME` with `platforms;android-35`, `build-tools;35.0.0`; later tasks run `./gradlew`.
- Produces: `docs/dev-environment.md` recording what was installed and any SDK path exports needed.

- [ ] **Step 1: Install JDK 17+** (prefer `brew install openjdk@17` — Homebrew is present; fall back to apt if brew fails). Verify: `java -version` prints 17+.
- [ ] **Step 2: Install Android cmdline-tools, accept licenses, and install `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`** via sdkmanager. Set/record `$ANDROID_HOME`. Verify: `sdkmanager --list_installed` shows the three packages.
- [ ] **Step 3: Check phone connectivity** — `adb devices`. If no device: note in `docs/dev-environment.md` that on-device tasks (5–6) are blocked until the phone is connected with USB debugging, and continue with Tasks 2–4.
- [ ] **Step 4: Write `docs/dev-environment.md`** (versions, paths, phone status).

### Task 2: Project scaffolding — builds a signed-nothing debug APK

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts` (root), `gradle.properties`, `gradle/wrapper/*` (via `gradle wrapper`), `.gitignore`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml` (MainActivity only for now)
- Create: `app/src/main/java/dev/hasan/usbtethertile/MainActivity.kt` (empty activity with a "Hello" layout for now)
- Create: `app/src/main/res/values/strings.xml`, `values/themes.xml`, drawable app icon (adapted from a stock material "usb" vector)
- Create: `LICENSE` (MIT, copyright Hasan)

**Interfaces:**
- Consumes: Task 1 toolchain.
- Produces: package `dev.hasan.usbtethertile`, app label "USB Tether", `MainActivity` exists at that FQCN; gradle wrapper works.

Key values for `app/build.gradle.kts`: `minSdk = 33`, `targetSdk = 35`, `compileSdk = 35`, `applicationId = "dev.hasan.usbtethertile"`, Kotlin jvmTarget 17, no Compose, dependency line: `implementation("dev.rikka.shizuku:api:13.1.5")` and `implementation("dev.rikka.shizuku:provider:13.1.5")` (added now, used by Task 4).

`AndroidManifest.xml` must include the Shizuku provider declaration (required for the API to function):

```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

- [ ] **Step 1: Initialize git repo** (`git init`, initial commit of docs).
- [ ] **Step 2: Write the scaffolding files above** with the pinned values.
- [ ] **Step 3: Run `./gradlew :app:assembleDebug`** — Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 4: Commit** — `git add -A && git commit -m "chore: scaffold usb-tether-tile app"`

### Task 3: Pure logic + unit tests

**Files:**
- Create: `app/src/main/java/dev/hasan/usbtethertile/TetherState.kt` (iface matching + tile-state computation, pure)
- Create: `app/src/main/java/dev/hasan/usbtethertile/ShizukuStatus.kt` (enum + mapping, pure)
- Create: `app/src/main/java/dev/hasan/usbtethertile/SettingsFallback.kt` (fallback intent list builder, pure)
- Test: `app/src/test/java/dev/hasan/usbtethertile/TetherStateTest.kt`, `ShizukuStatusTest.kt`, `SettingsFallbackTest.kt`
- Modify: `app/build.gradle.kts` — add JUnit test deps (`junit:junit:4.13.2`, `kotlin.test`)

**Interfaces:**
- Produces (used by Tasks 4–5):
  - `enum class ShizukuStatus { READY, NEEDS_PERMISSION, NOT_RUNNING, NOT_INSTALLED }`
  - `fun usbTetherIface(names: Collection<String>): String?` — first name starting with `usb`/`ncm`/`rndis`, else null
  - `data class TileModel(state: TileState, subtitle: String?)` with `enum class TileState { ACTIVE, INACTIVE }`
  - `fun tileModel(status: ShizukuStatus, usbIface: String?): TileModel` — READY → subtitle "USB tethering"; anything else → subtitle "needs Shizuku"; state ACTIVE iff `usbIface != null`
  - `fun mapStatus(installed: Boolean, pingBinder: Boolean, granted: Boolean): ShizukuStatus` — not installed → NOT_INSTALLED; installed+no ping → NOT_RUNNING; ping+granted → READY; ping+!granted → NEEDS_PERMISSION
  - `fun tetherSettingsIntents(): List<Intent>` — the three fallbacks in Global-Constraints order (intents built from action string / `setClassName`, no Context needed)

- [ ] **Step 1: Write failing tests** — TetherStateTest: `listOf("wlan0", "ncm0")` → `ncm0`; `listOf("wlan0")` → null; `listOf("usb0")` → `usb0`; `listOf("rndis0")` → `rndis0`; empty → null. TileModel: `(READY, "ncm0")` → ACTIVE/"USB tethering"; `(NOT_RUNNING, null)` → INACTIVE/"needs Shizuku"; `(READY, null)` → INACTIVE/"USB tethering"; `(NEEDS_PERMISSION, "usb0")` → ACTIVE/"needs Shizuku". ShizukuStatusTest: `(false, _, _)` → NOT_INSTALLED; `(true, false, _)` → NOT_RUNNING; `(true, true, true)` → READY; `(true, true, false)` → NEEDS_PERMISSION. SettingsFallbackTest: first intent action == `android.settings.TETHER_SETTINGS`; second component className == `com.android.settings.TetherSettings`; third action == `android.settings.WIRELESS_SETTINGS`; list size == 3.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest` — Expected: FAIL (classes don't exist).
- [ ] **Step 3: Implement the three files** per the Interfaces block.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` — Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat: tile state, shizuku status, settings fallback logic"`

### Task 4: Shizuku gate + privileged toggle

**Files:**
- Create: `app/src/main/java/dev/hasan/usbtethertile/ShizukuGate.kt`
- Create: `app/src/main/java/dev/hasan/usbtethertile/PrivilegedTether.kt`
- Test: `app/src/test/java/dev/hasan/usbtethertile/ShizukuGateTest.kt` (only the testable seams; the binder call itself is verified on-device in Task 5)

**Interfaces:**
- Consumes: Task 3 `ShizukuStatus`, `mapStatus`; Shizuku API (`Shizuku`, `ShizukuBinderWrapper`), HiddenApiBypass (`org.lsposed.hiddenapibypass:hiddenapibypass:6.1` — bump to latest available if 6.1 doesn't resolve).
- Produces:
  - `object ShizukuGate { fun check(): ShizukuStatus }` — installed = package `moe.shizuku.privileged.api` resolvable via `context.packageManager`; ping = `Shizuku.pingBinder()`; granted = `Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED`. Also `fun addPermissionResultListener(onResult: (Int) -> Unit)` wrapping `Shizuku.addRequestPermissionResultListener` for MainActivity.
  - `object PrivilegedTether { fun setUsbTethering(enable: Boolean, callerPkg: String): Int }` — returns the binder result int (0 = success).

Implementation for the privileged call (algorithm not determined by signature — pin it):

```kotlin
fun setUsbTethering(enable: Boolean, callerPkg: String): Int {
    // Once at first use (idempotent): exempt android.net hidden members so the
    // reflective lookup is legal on API 28+.
    HiddenApiBypass.addHiddenApiExemptions("Landroid/net/")
    val binder = ShizukuBinderWrapper(ServiceManager.getService("connectivity"))
    val proxy = Class.forName("android.net.IConnectivityManager\$Stub")
        .getMethod("asInterface", IBinder::class.java)
        .invoke(null, binder)!!
    return Class.forName("android.net.IConnectivityManager")
        .getMethod("setUsbTethering",
            Boolean::class.javaPrimitiveType, String::class.java)
        .invoke(proxy, enable, callerPkg) as Int
}
```

Call this on a background thread (the tile uses a single-thread executor), never the QS main thread.

- [ ] **Step 1: Write failing test** — ShizukuGateTest: a seam function `mapRaw(installed: Boolean, ping: Boolean, granted: Boolean)` delegates to Task 3's `mapStatus`; test asserts all four mappings again through the seam and that `ShizukuGate.check()` compiles against the Shizuku API surface (compile-time test only; runtime branches covered by seam tests).
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest` — Expected: FAIL.
- [ ] **Step 3: Implement** `ShizukuGate` and `PrivilegedTether` per above.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` — Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat: shizuku gate and privileged usb tether toggle"`

### Task 5: Tile service wiring

**Files:**
- Create: `app/src/main/java/dev/hasan/usbtethertile/UsbTetherTileService.kt`
- Modify: `app/src/main/AndroidManifest.xml` — register the service
- Modify: `app/src/main/res/values/strings.xml` — tile label

**Interfaces:**
- Consumes: Task 3 (`tileModel`, `usbTetherIface`, `TileModel`, `TileState`), Task 4 (`ShizukuGate`, `PrivilegedTether`), Task 2 scaffolding.
- Produces: tile `dev.hasan.usbtethertile/.UsbTetherTileService` pinnable in QS.

Service registration:

```xml
<service
    android:name=".UsbTetherTileService"
    android:icon="@drawable/ic_tile"
    android:label="@string/tile_label"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>
```

Behavior (signature `class UsbTetherTileService : TileService()`):
- `onStartListening`: read state via `ConnectivityManager.getTetheredIfaces()` (public), `ShizukuGate.check()`, `tileModel(...)`, `updateTile(...)`. Register `ConnectivityManager.registerTetheringEventCallback(mainExecutor, callback)` whose `onTetheredInterfacesChanged` re-runs the same update. Unregister in `onStopListening`.
- `onClick`: single-thread executor: `status = ShizukuGate.check()`; READY → `PrivilegedTether.setUsbTethering(!tethered, packageName)`; result 0 → update tile; nonzero → toast "Connect a USB cable" (or "Failed to toggle") and open the Task 3 fallback (first resolvable intent of `tetherSettingsIntents()`). NEEDS_PERMISSION → `startActivity` on MainActivity (with `FLAG_ACTIVITY_NEW_TASK`). NOT_RUNNING/NOT_INSTALLED → open first resolvable fallback intent.
- Tile subtitle per `TileModel.subtitle`.

- [ ] **Step 1: Implement** `UsbTetherTileService` and manifest/strings changes as pinned above.
- [ ] **Step 2: Run** `./gradlew :app:assembleDebug` — Expected: SUCCESS.
- [ ] **Step 3: Manual on-device verification** (requires phone connected; if blocked, record in `docs/dev-environment.md` and stop for user):
   1. `adb install -r app/build/outputs/apk/debug/app-debug.apk`
   2. Confirm shell privileges: `adb shell dumpsys package com.android.shell | grep -i TETHER_PRIVILEGED` — expected: permission listed as granted. If absent, STOP and report: the privileged call will be denied and the approach needs rework.
   3. Start Shizuku on the phone (wireless debugging pairing or root), grant the app.
   4. Pin "USB tether" tile, plug USB into this machine, tap tile → expected: `adb shell ip link` on the phone shows `usb0`/`ncm0` state UP and this machine gets a USB network interface; tile ACTIVE.
   5. Tap again → iface gone; tile INACTIVE.
   6. Stop Shizuku, tap tile → expected: tethering settings page opens.
   7. Toggle tethering from system Settings while QS panel open → expected: tile state follows (Review Focus 5).
   8. Unplug cable, tap tile with Shizuku running → expected: toast, no crash, tile INACTIVE (Review Focus 1).
- [ ] **Step 4: Commit** — `git commit -am "feat: usb tether quick settings tile"`

### Task 6: MainActivity permission flow + fallback polish

**Files:**
- Modify: `app/src/main/java/dev/hasan/usbtethertile/MainActivity.kt`, `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/dev/hasan/usbtethertile/ShizukuGate.kt` (listener wiring if needed)

**Interfaces:**
- Consumes: Task 4 `ShizukuGate.check()` / permission listener; Task 3 fallback intents (for a "Open tethering settings" button).
- Produces: MainActivity shows Shizuku status text, a "Grant Shizuku permission" button (`Shizuku.requestPermission(requestCode)`, result via listener refreshes status), and an "Open tethering settings" button; when Shizuku is not installed, the button becomes "Install Shizuku" opening `https://shizuku.rikka.app/`.

- [ ] **Step 1: Implement** MainActivity UI per above (plain XML views).
- [ ] **Step 2: Run** `./gradlew :app:assembleDebug` — Expected: SUCCESS.
- [ ] **Step 3: Manual on-device verification** — fresh Shizuku state (revoke via Shizuku app): tap tile → MainActivity opens → grant dialog appears → after grant, tile toggles work. Shizuku stopped: tile tap opens tethering settings (Review Focus 2/3 pinned).
- [ ] **Step 4: Commit** — `git commit -am "feat: shizuku permission flow in main activity"`
- [ ] **Step 5: Final branch review** — `git diff` review of the whole branch vs the spec's Goals/Non-goals (USB only, no network permission), then report to user with install/usage instructions.
