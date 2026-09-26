# Personal Tools — v2: profiles + SQLite

## New requirements (from user)
- **Clicker tab** owns: profile selection, points (with per-point name),
  time-between-taps (per-point delay), Start/Stop, and the run controls
  (floating bar / notification + Show/Hide).
- **Named profiles + named points**: profiles = saved point-sets for different
  apps/purposes; each point has a label, x/y, delay.
- **Setup tab** = permissions + test only (no point selection).
- Storage: **real database** — raw SQLite via SQLiteOpenHelper (no new deps,
  keeps the app minimal). Replaces PointStore (JSON-in-prefs).

## Schema (SQLiteOpenHelper, v1)
- profiles(id PK, name, created_at)
- points(id PK, profile_id FK→profiles ON DELETE CASCADE, label, x, y,
  delay_ms, position)
- meta(key PK, value)  — active_profile, control_mode

## Tasks
- [ ] data/Database.kt — SQLiteOpenHelper, FK on, create tables
- [ ] data/ClickRepository.kt — profiles/points/meta CRUD + active profile +
      control mode; ensureDefaultProfile()
- [ ] model/Profile.kt; extend model/ClickPoint.kt (id,label,position)
- [ ] delete data/PointStore.kt (keep ClipboardStore for now)
- [ ] service/ClickerAccessibilityService.kt — use repo; add testTap()
- [ ] service/OverlayController.kt — selector adds to active profile via repo
- [ ] ui/MainActivity.kt — profiles spinner (new/rename/delete), points list
      with labels, add-by-coords + pick-on-screen, Start/Stop, control mode +
      show/hide (all on Clicker); Setup = accessibility status + Test tap
- [ ] layouts: content_clicker.xml (rework), content_setup.xml (perm+test),
      row_point.xml (+label); strings
- [ ] Build + verify on emulator (profiles, named points, taps, controls)

## Prior verification (v1, on emulator Android 14)
- Accessibility overlay bar + draggable crosshair selector WORK (no
  SYSTEM_ALERT_WINDOW — TYPE_ACCESSIBILITY_OVERLAY, appop=NONE).
- dispatchGesture taps WORK (injected tap switched the app's tab).
- Point selector persists exact coordinates.
- Fixed: invisible selected tab; per-tab data refresh on switch.

## Review (v2 — verified on emulator, Android 14 / SDK 34)
- Build clean (SQLite, zero new deps). Installs + launches, no crash.
- **Clicker tab** as requested: Profile spinner + NEW/RENAME/DELETE; named-point
  inputs (Name / X / Y / ms-between); Add point + Pick on screen; Start/Stop;
  Run controls (Floating button / Notification, Show/Hide) all on this tab.
- **Setup tab** = permissions + test only. Accessibility shows ON; "Send test
  tap" → **"Test tap succeeded ✓"** (dispatchGesture completion callback fired).
- **SQLite** verified via sqlite3: profiles(id,name,created_at),
  points(id,profile_id,label,x,y,delay_ms,position), meta(active_profile,
  control_mode). FK cascade on profile delete.
- **Pick on screen** selector floats over OTHER apps (home screen) as a
  TYPE_ACCESSIBILITY_OVERLAY, tracks live coords, and confirmed point persisted
  to the active profile as P1 (251,501) delay 500 — exact.
- Architecture: overlays hosted from the AccessibilityService
  (TYPE_ACCESSIBILITY_OVERLAY) — NO SYSTEM_ALERT_WINDOW / foreground service.
  Manifest permission is now only POST_NOTIFICATIONS.

## Known device notes
- MIUI (phone) reverts accessibility enabled outside its UI and gates overlays;
  the accessibility-overlay approach avoids the overlay gate. Enable the service
  via MIUI's own dialog so it persists.
- Add-by-keyboard works; only host-side blind taps were imprecise in testing.
