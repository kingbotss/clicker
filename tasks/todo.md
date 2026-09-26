# Overlay fixes: draggable bar, colored point markers, pick across apps

## Bugs (from user, on-device)
1. Can't drag the floating control bar.
2. Click points should have visible color (markers on screen).
3. Can't choose a point while switching apps (picker blocks app switching).

## Root causes
1. `OverlayController.dragListener.onTouch` returns `false` on ACTION_DOWN, so
   the framework never delivers ACTION_MOVE/UP → drag dead. Fix: return true.
2. `startPicker()` adds a MATCH_PARENT overlay whose `setOnTouchListener` returns
   true for the whole screen → consumes every touch → can't reach recents/home to
   open the target app. Fix: pass-through picker.
3. Saved points never rendered on screen; `marker` color unused.

## Plan
- [ ] Fix drag: dragListener returns true (consume gesture). Reuse the same
      draggable pattern for the crosshair.
- [ ] Redesign picker as pass-through:
      - small draggable crosshair window (WRAP_CONTENT, moves via its own touch);
      - small bottom toolbar window (live coords + ✓ confirm / ✕ cancel);
      - NO full-screen touch grabber → underlying app still gets touches, so the
        user can open recents / launch the target app, then position + confirm.
      - point coord = crosshair window center in screen space (LAYOUT_IN_SCREEN).
- [ ] Colored point markers (bug 2): render each active-profile point as a small
      colored numbered dot overlay (marker color, FLAG_NOT_TOUCHABLE so it never
      eats touches). Show with the control bar + refresh after add/delete/profile
      switch; clear on teardown.
- [ ] New layouts: overlay_crosshair.xml, overlay_pickbar.xml, marker_dot dot
      drawable + row. Keep OverlayController focused (<400 lines).
- [ ] Build (assembleRelease), verify config unchanged, commit + push + re-release.

## Review (done 2026-09-27)
- Bug 1 (drag): makeDraggable now returns true on DOWN/MOVE/UP → bar drags.
- Bug 2 (color): numbered colored dots (marker color, non-touchable) drawn per
  active-profile point; shown with controls, refreshed on add/edit/delete/switch.
- Bug 3 (pick across apps): picker rewritten pass-through — small draggable
  crosshair + top toolbar only; rest of screen touchable so the user can open the
  target app then position + confirm. Point = crosshair centre (LAYOUT_IN_SCREEN).
- Removed dead overlay_selector.xml. assembleRelease BUILD SUCCESSFUL.
- Install still MIUI-gated (INSTALL_FAILED_USER_RESTRICTED); APK staged at
  /sdcard/Download/TapMate.apk. On-device interaction can't be host-driven on
  MIUI — user verifies drag / markers / cross-app pick by hand.
