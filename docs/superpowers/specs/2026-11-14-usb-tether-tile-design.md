# USB Tether Tile — Design (short)

Date: 2026-11-14
Status: pending approval

## Intent

A quick settings tile on the user's Android 15+ phone that toggles USB
tethering with one tap, so they no longer navigate through Settings.

## Goals

- One-tap on/off toggle of USB tethering from the QS panel.
- Correct live tile state (active when a USB iface is tethered).
- Graceful fallback: when Shizuku is not available (not installed, not
  running, or permission not granted), tapping the tile opens the
  system tethering settings page instead of failing silently.

## Non-goals / anti-goals

- No Wi-Fi hotspot, Bluetooth tethering, or Ethernet tethering — USB only.
- No root requirement (root path exists but is not the supported setup).
- No auto-enable on cable plug-in, no widget, no scheduling.

## Constraints

- USB tethering has no public toggle API; the hidden
  `ConnectivityManager.setUsbTethering` requires `TETHER_PRIVILEGED`,
  which shell (Shizuku) has.
- Shizuku must be installed and running on the phone (it can auto-start
  on boot); the tile app requests Shizuku permission on first use.
- Hidden-method call signatures may vary across builds; calls are
  verified against Android 15 behavior, with the Settings fallback as
  the safety net on failure.

## Prior art (checked 2026-11-14)

No maintained app provides exactly this (USB tethering on/off QS tile via
Shizuku). Closest: Shizzi (tethering bypass via TUN, different purpose),
EtherTether (Ethernet adapters only), Quick-Tile Settings (USB debugging +
Private DNS tiles, proves the tile+Shizuku pattern). The technique itself
is well-proven; building our own is justified.

## Approaches considered

1. **Shizuku-based tile (chosen)** — no root, one-time setup; matches
   proven prior art (existing open-source tethering tile apps). Tradeoff:
   depends on Shizuku running; hence the fallback.
2. **Root-only tile** — simpler runtime call (request `su`), but requires
   a rooted phone. Rejected: phone is not rooted.
3. **Deep-link-only tile** — zero setup, tap opens tethering settings.
   Rejected as primary: it's a shortcut, not a switch. It survives as
   the fallback behavior.

## How it works (decided)

- Single Kotlin app, one module, a `TileService` the user pins to QS.
- **Toggle:** through Shizuku's binder wrapper, call the connectivity
  service's `setUsbTethering(enabled, callerPackage)` as shell.
- **State:** read `ConnectivityManager.getTetheredIfaces()` and watch
  tethering events via the public `TetheringEventCallback` while the
  tile is listening; tile renders ACTIVE / INACTIVE.
- **Shizuku status:** ping the Shizuku binder and check permission;
  if unavailable/not granted, tile renders with a "needs Shizuku"
  subtitle in INACTIVE state and tap opens the tethering settings
  screen (`TetherSettings` component, falling back to wireless
  settings if the direct component launch fails).
- **Failure of the privileged call:** show a toast and open the
  settings page (same fallback path).

## Left to the builder

- Exact mechanism for the hidden call (reflection vs. compiled AIDL
  stubs) — builder's call, following prior art.
- Package id, icon, minor UX copy.
- Build toolchain specifics (Gradle + Kotlin, minSdk 33, target 35
  baseline).
