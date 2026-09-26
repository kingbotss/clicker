# Personal Tools — Autoclicker + Clipboard Saver

A minimal, **no-root** Android app (Android 9+ / API 28) with two features that
share one Accessibility service:

1. **Autoclicker** — automated taps at one or more screen points, looping until
   you stop. Points live in **named profiles** (one per app/purpose), each point
   has a **name**, coordinates, and its own **time-between-taps** (ms). Pick
   points by dragging an on-screen crosshair (works over any app) or by typing
   coordinates.
2. **Clipboard saver** — auto-saves copied text to a history you can browse,
   re-copy, and delete. Persists across reboots.

## Permissions — just one

The only manifest permission is `POST_NOTIFICATIONS` (Android 13+, for the
optional notification controls). There is **no** `SYSTEM_ALERT_WINDOW` and **no**
foreground service: the floating controls and the point picker are drawn as a
`TYPE_ACCESSIBILITY_OVERLAY` from the accessibility service, which also performs
the taps (`dispatchGesture`) and captures the clipboard. You enable the
Accessibility service once, in the Setup tab.

## Build

Requires JDK 17 and the Android SDK (platform 35). Then:

```bash
./gradlew :app:assembleDebug        # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug         # install to a connected device/emulator
```

Or open the project in Android Studio and press Run.

## First-run setup (Setup tab)

1. **Enable the Accessibility service** — tap *Open settings*, turn on
   "Personal Tools Clicker". Required for taps, clipboard capture, and the
   on-screen controls. (On MIUI/Xiaomi, accept the security dialog; enabling it
   through the phone's own UI makes it stick.)
2. **Test** — tap *Send test tap* to confirm the service can inject touches.

## Clicker tab

- **Profiles**: pick a profile from the dropdown; NEW / RENAME / DELETE to manage
  saved setups for different apps.
- **Points**: add by coordinates (Name, X, Y, ms-between) or tap **Pick on
  screen** — drag the red crosshair anywhere (even over another app) and tap ✓.
  Tap a point row to edit its name/timing; use its Delete to remove it.
- **Start / Stop** runs the active profile's points top-to-bottom on a loop.
- **Run controls** (floating button or notification) let you start/stop and add
  points while you're inside another app.

## Clipboard tab

Copy text anywhere; it appears here (reopen the tab to refresh). Tap an item to
re-copy; Delete per item or Clear all.

## Storage

A single SQLite database (`personal_tools.db`): `profiles`, `points`
(foreign-keyed to profiles, cascade delete), and a `meta` key/value table
(active profile, control mode). Clipboard history is kept in SharedPreferences.

## Known limitations (honest notes)

- **Background clipboard capture varies by OEM.** Android 10+ restricts
  background clipboard reads; the accessibility service is the best-effort
  no-root path, and the code degrades gracefully if a device denies it.
  Foreground capture always works.
- **Coordinates are absolute pixels** — points are device/orientation specific.

## Project layout

```
app/src/main/java/com/personal/tools/
  model/ClickPoint.kt, Profile.kt        data classes
  data/Database.kt                        SQLiteOpenHelper (profiles/points/meta)
  data/ClickRepository.kt                 profiles/points/meta + active profile
  data/ClipboardStore.kt                  clipboard history (prefs)
  service/ClickEngine.kt                  tap loop (dispatchGesture)
  service/ClickerAccessibilityService.kt  taps + clipboard + overlay host + test
  service/OverlayController.kt            floating bar + crosshair selector
  service/ControlNotification.kt          notification with Start/Stop + Hide
  service/ControlReceiver.kt              notification action receiver
  ui/MainActivity.kt                      three tabs (Clicker / Clipboard / Setup)
  ui/ClipboardAdapter.kt                  clipboard list
```

For personal use.
