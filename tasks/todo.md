# Rename app identity → TapMate / com.kingboat.automa

## Why
Play Protect flags the sideloaded accessibility autoclicker. Renaming to a fresh
package id resets the (locally flagged) package reputation and drops the generic
`com.personal.tools` identity. NOTE: behavior (accessibility gesture injection) +
sideload + unknown-signer reputation are the primary Play Protect triggers; the
rename helps but is not guaranteed to fully clear the flag.

## Decisions (from user)
- applicationId / namespace: `com.kingboat.automa`
- app display name: `TapMate`

## Tasks
- [ ] git mv source dir com/personal/tools → com/kingboat/automa
- [ ] Replace `com.personal.tools` → `com.kingboat.automa` in all .kt (package,
      imports, ControlReceiver action constants)
- [ ] build.gradle.kts: namespace + applicationId
- [ ] settings.gradle.kts: rootProject.name → "TapMate"
- [ ] strings.xml: app_name → "TapMate"; accessibility_label → "TapMate"
- [ ] themes.xml + manifest: Theme.PersonalTools → Theme.TapMate
- [ ] Database.kt: db file name personal_tools.db → tapmate.db (fresh pkg = new data)
- [ ] Clean build (assembleRelease) + verify no com.personal / PersonalTools refs
- [ ] Uninstall old com.personal.tools; install renamed APK; launch

## Review (done 2026-09-26)
- Renamed everywhere: namespace + applicationId = `com.kingboat.automa`; app_name
  + accessibility_label = "TapMate"; Theme.PersonalTools → Theme.TapMate; db file
  → tapmate.db; rootProject.name → TapMate; ControlReceiver actions →
  com.kingboat.automa.TOGGLE/.HIDE. Source dir git-mv'd to com/kingboat/automa.
- `grep -ri personal` over src/gradle = clean. `assembleRelease` BUILD SUCCESSFUL
  (2m11s), signed release APK produced. Merged manifest package = com.kingboat.automa.
- Old com.personal.tools was never actually installed on device (pm path empty) —
  prior session's install had failed.
- INSTALL BLOCKED by MIUI: `INSTALL_FAILED_USER_RESTRICTED` — adb install refused.
  Needs on-device action (see below). APK staged at /sdcard/Download/TapMate.apk.

## Next (user action on phone — MIUI gate)
Either: Developer options → enable "Install via USB" (+ "USB debugging (Security
settings)") with Mi account signed in, then rerun `adb install -r`; OR open the
phone's Files app → Download → TapMate.apk → Install, accepting MIUI prompts.
Play Protect may still warn on first launch (accessibility autoclicker behavior);
"Install anyway"/"Install without scanning" if you trust it.
