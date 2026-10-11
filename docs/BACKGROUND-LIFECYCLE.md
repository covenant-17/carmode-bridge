# Background lifecycle audit — 0.1.8

This audit describes the 0.1.8 lifetime separation. The independent HCN foreground runtime remains current. Later boot experiments did not establish reliable firmware startup; the user-confirmed route is the optional [Home proxy](HOME-PROXY.md). The checks below are regression procedures, not proof that firmware preserves Accessibility permission or always delivers boot broadcasts.

## Findings and boundaries

- No `disableSelf`, disabling ModeService component, secure-settings writes or automatic Accessibility grants exist in app code.
- `killProcess` and `System.exit` occur only in the fatal uncaught-exception handler after report persistence and delegation to Android. They are not called from Activity closure or Recents removal.
- MainActivity destruction stops only UI refresh, its local preview and its own executor. Activity pause/back can cancel a selection, not stop Accessibility or the foreground runtime.
- Before this change HCN receiver ownership was tied to Accessibility. A revoked permission or disconnected service therefore also removed the HCN receiver. The user reports the head unit revokes access while the phone does not; the app cannot force firmware to keep the permission enabled.
- A stale static `ModeService.active` reference previously survived until onDestroy. It now clears onUnbind so the UI and exports distinguish permission from real connection.

## Implemented lifetime separation

ModeRuntimeService owns the only HCN receiver, HCN overlay and screen-close cancellation. It uses an ongoing low-priority notification, START_STICKY and explicit stopWithTask=false. onTaskRemoved records evidence without stopping the service. Activity destruction neither unregisters its receiver nor stops its timer.

The service starts from app launch, relevant settings changes, permitted Accessibility connection, BOOT_COMPLETED and MY_PACKAGE_REPLACED. Boot/update require auto-start. Normal handling respects enabled/auto-start/manual activation; diagnostic recording may keep a passive receiver alive with handling disabled. Turning off handling and recording stops only ModeRuntimeService via stopService. A sticky restart with no remaining work, or pending crash recovery, uses stopSelf; neither targets Accessibility.

Accessibility remains responsible only for learned KeyEvent and window inspection. HCN input does not need it and survives its unbind/destroy while the runtime remains alive. MODE/no-press/other-button guided scenarios can run using HCN without Accessibility; keyboard/legacy key tests still require it. No duplicate HCN registration is kept in ModeService.

## Evidence and reconnection investigation

At process/app launch, Activity resume and service lifecycle boundaries, compare exact membership in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` (including shortened component notation) with actual ModeService connection. Also capture master Accessibility state, component enabled setting, foreground-runtime state, PID, battery exemption and background restriction. Permission-query errors remain explicit rather than becoming a false revoked result.

Persist `accessibility_created`, `accessibility_connected`, `accessibility_unbound`, `accessibility_interrupted`, `accessibility_destroyed`; store the last callback/time/PID/state separately from rotated event logs. Log Activity close/configuration status, runtime start/stop/task removal, and process start with previous callback evidence. While the foreground runtime lives, compare Accessibility state every five seconds and log changes even outside diagnostic recording. Exports include current and last-callback evidence.

- `disabled`: permission no longer lists CarMode; do not attempt rebind or permission writes.
- `enabled_disconnected`: permission remains, but Android has not connected. Subsequent lifecycle/process records show whether connection returns or a process/service was terminated. This is not proof of a particular OEM kill policy. There are no repeated rebind loops or hidden Activity relaunches.
- Process disappearance without onDestroy is possible. A previous connected record with a new PID is evidence of interrupted lifetime, not a definitive exit reason. Java crash reports remain separate; native/firmware termination can leave no crash report.

Android owns [Accessibility service lifecycle](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService). A [sticky foreground service](https://developer.android.com/reference/android/app/Service) improves lifetime but does not override force-stop, revoked permissions, disabled boot delivery or OEM policy. No automatic Accessibility approval or vendor whitelist modification is attempted.

## Head-unit acceptance (not yet verified)

1. Install 0.1.8 over the previous version. Enable handling, HCN, auto-start and Overlay. Verify the background-service status and ongoing notification. If using Accessibility, explicitly grant it once.
2. Start ordinary recording (not a guided test), verify MODE, swipe CarMode from Recents, wait 30 seconds and press MODE without reopening the app. Check one/two/three presses and no duplicate menu. Note whether the foreground notification remains.
3. Reopen only after this check; export JSON. Compare runtime_task_removed, service callbacks and secure permission/connection evidence. If firmware removed permission, report that fact instead of claiming the original permission-retention criterion passed.
4. Explicitly switch off Accessibility manually and repeat the HCN swipe test. HCN should still work; no windows or global KeyEvents can be collected in this configuration.
5. Enable auto-start, cold reboot, and test MODE without opening CarMode. Also test sleep/wake separately. There is no Android 10 runtime approval dialog for BOOT_COMPLETED. If the head unit has a vendor auto-start/background whitelist, allow CarMode there manually; app-detail and battery-settings links are provided, not a guessed OEM settings component.
6. Negative cases: disabled handling plus stopped recording stops the runtime; missing Overlay passes native MODE through; crash recovery does not resume handling. Force-stop is explicitly outside the Recents-swipe criterion and may require manual app launch.

JVM tests cover permission parsing/status and readiness/recording policy; packaging checks detect compiler-error stubs. They do not establish on-device survival, firmware permission retention or boot broadcast delivery.
