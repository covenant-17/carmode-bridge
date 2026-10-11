# Native actions — 0.1.9

Current status (0.1.22): app assignments use only three app pickers; no separate launch-test/manual-component controls remain. Music HTTP is implemented as an optional action since 0.1.17, and successful launches no longer show the obsolete ZLink explanation. Sections describing 0.1.9 buttons or the initially excluded HTTP request are historical, superseded by the updates below. Tablet MacroDroid modes remain removed.

HTTP update in 0.1.17: the user clarified that the phone receives the request and offers music playback. The outgoing GET from Music.macro is now an optional, default-off post-Music action with an editable address, implemented without MacroDroid on the tablet. Details and differences from the macro: [MUSIC-HTTP.md](MUSIC-HTTP.md). Earlier “not transferred” descriptions below apply to versions through 0.1.16; other excluded macro actions remain excluded.

UI update in 0.1.16: assignments are now three app-picker buttons (camera/music/maps), showing the selected app. Launch tests, manual component entry, all-Activity picker and separate restore buttons were removed from this screen. Existing explicit components remain valid; the app picker retains a clear-assignment option to return to the default. Popup labels/selection behavior and native launch semantics are unchanged. Historical 0.1.9 test-button descriptions below are superseded by this update. At that version, the HTTP action was still unimplemented pending clarification; 0.1.17 subsequently added it.

The supplied `360.macro` and `Music.macro` replace stock-menu app launches, not the MODE input itself. CarMode instead executes the selected destination directly after its two-second countdown. The HCN receiver and foreground service remain independent of Accessibility and MacroDroid.

## Transferred

- Camera default: launcher Activity of `ivicar.cn.ivicaravm`. Request exclusion from Recents for this default camera task, as in the macro. Existing tasks can retain their original Recents behavior.
- Music default: launcher Activity of `com.zjinnova.zlink`.
- Maps also opens ZLink; neither supplied macro implements Music/Maps section selection.
- Existing explicit Activity assignments take precedence, including assignments previously paired with MACRODROID mode. They now execute directly.
- Each card has a real launch test with confirmation, and a restore-default button. Missing/unavailable targets produce a visible error and a diagnostic entry.

Package launch uses Android's [getLaunchIntentForPackage](https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentForPackage(java.lang.String)), not a guessed OEM Activity name.

## Not transferred

- Application-launched triggers, two Back actions and delays: these undo unwanted stock app launches. Our direct route bypasses those launches; it does not globally redirect apps opened elsewhere.
- Closing Radio/Music/Bluetooth apps: not required for the intended direct route. No force-stop, root commands or Accessibility navigation are introduced.
- Setting selected audio streams to zero: deliberately omitted, not equivalent to stopping the tuner. Stream settings could silence wanted playback; OEM audio-source behavior still needs a head-unit check.
- The active local HTTP GET in Music: its server and purpose are unknown. No network request is made by the new route.
- Disabled shell/media, call, WhatsApp and email experiments: remain disabled and are not reproduced.

## Removed

MacroDroid execution modes, outgoing automation broadcasts, target-package setting, Intent-action copy buttons, broadcast tests and MacroDroid-specific installed-state probes. Old mode/target settings are removed on upgrade; other settings and assignments are retained. Generic reporting of other enabled Accessibility key filters remains.

## Head-unit acceptance

1. Install 0.1.9 over the previous APK, preserving settings. Disable MacroDroid completely for the test.
2. Restore default apps on the camera/Music/Maps cards if old assignments point to stock camera or radio. Use each real launch test.
3. Enable HCN MODE and overlay permission. One MODE selects 360; two select ZLink Music; three select ZLink Maps. Wait two seconds after the final press.
4. Check that stock camera/radio do not flash on screen, and that radio does not remain audible beneath ZLink. If it does, export diagnostics; do not treat volume-zero as a confirmed solution.
5. Swipe away CarMode's settings Activity, then repeat MODE. Check full reboot and head-unit sleep/wake separately.

The build and JVM tests do not verify OEM broadcast suppression, sound routing, background launch permissions or ZLink behavior on the real tablet. Android Auto screen switching and automatic playback are not claimed.
