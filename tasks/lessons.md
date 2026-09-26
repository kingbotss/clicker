# Lessons

## Play Protect flags accessibility apps by reputation, not permissions/name
- The "this app can request sensitive data / risk of theft or fraud" warning
  fires for ANY app that binds an AccessibilityService with canPerformGestures.
  It is behavior/capability-based. Renaming the package (com.personal.tools →
  com.kingboat.automa), changing the label, or re-signing does NOT remove it.
- Verified against the Play Store reference "Auto Clicker"
  (com.truedevelopersstudio.automatictap.autoclicker 2.3.0): it uses the SAME
  BIND_ACCESSIBILITY_SERVICE + canPerformGestures, and requests MORE overall
  (INTERNET, billing, AD_ID, ad-services, install-referrer, foreground service,
  wake lock) yet isn't hard-flagged — solely because of Play distribution +
  install reputation + trusted signing key. A sideloaded build can't replicate
  that. Don't promise a rename/manifest change will clear Play Protect.
- Its a11y config is minimal: canPerformGestures=true, feedbackGeneric,
  flagDefault, settingsActivity set, NO accessibilityEventTypes, no
  canRetrieveWindowContent. Good template — mirror it for a small honest
  footprint, but understand it's hygiene, not an evasion.
- To USE a sideloaded a11y app on Android 13+: App info → ⋮ → "Allow restricted
  settings", then enable the service, then accept the Play Protect prompt.
- Inspect a reference APK with build-tools aapt (apktool failed here):
  `aapt dump badging|permissions <apk>`; map the a11y config via
  `aapt dump --values resources <apk> | grep xml/accessibility` then
  `aapt dump xmltree <apk> res/<obfuscated>.xml`.

## Android UI
- **TabLayout on a colored background**: Material3's default selected-tab text +
  indicator use `colorPrimary`. If the TabLayout background is also the primary
  color, the selected tab becomes invisible. Always set explicit
  `app:tabTextColor` / `app:tabSelectedTextColor` / `app:tabIndicatorColor`
  when the tab bar has a solid brand background. (Caught on-device, fixed.)

## No-root overlays: use TYPE_ACCESSIBILITY_OVERLAY
- To draw a floating button / point-picker without SYSTEM_ALERT_WINDOW, add the
  window from the AccessibilityService with
  `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY` (2032), flags
  NOT_FOCUSABLE | LAYOUT_IN_SCREEN. This needs NO overlay permission and is not
  blocked by MIUI's overlay gate (which blocks TYPE_APPLICATION_OVERLAY). It also
  drops the foreground service. Confirmed against a production autoclicker via
  the D:/asc decompiler and verified on-device (window shows with appop=NONE).
- Touch rawX/rawY on a LAYOUT_IN_SCREEN overlay == dispatchGesture display
  coordinates, so a picked point maps 1:1 to where taps land.

## Testing: prefer the emulator over MIUI
- The emulator allows `settings put secure` and `input tap/text` freely, so
  accessibility can be enabled and the UI driven from the host. Use it for
  functional runs. Blind `input tap` on a dense form fights the soft keyboard —
  prefer keyboard-free paths (the overlay selector) or verify precise focus with
  a screenshot between steps.

## Device testing on Xiaomi / MIUI
- MIUI blocks, over plain adb, both `settings put secure ...`
  (WRITE_SECURE_SETTINGS) and `input tap/text/keyevent` (INJECT_EVENTS) unless
  the user enables "USB debugging (Security settings)", which needs a signed-in
  Mi account. So on MIUI you cannot auto-enable an accessibility service or
  drive the UI from the host — install + launch + screencap work; interaction
  and secure-setting toggles must be done by hand on the device.
