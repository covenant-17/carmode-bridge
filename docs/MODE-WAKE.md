# MODE cold start — 0.1.12

Historical investigation: this fallback remains in the app, but it did not establish reliable startup on the tested firmware. Use the user-confirmed [Home proxy](HOME-PROXY.md) for the current startup setup. Factory-list inspection is already complete; the older request to supply its APK below is investigation history, not a pending setup step.

AutoSource declares KSRC_BUTTON in its manifest and starts AutoSourceService on receipt, rather than declaring a boot receiver. CarMode now adds ModeWakeReceiver as a cold-start fallback, priority 998; the established dynamic receiver keeps priority 999 and remains the primary input path.

When the runtime is already alive, the fallback does not advance or abort. When cold, it requires handling readiness, HCN enabled, overlay permission, an abortable ordered broadcast, no learning/marked test, and a valid fresh DOWN or absent KEY_EVENT. It starts the foreground runtime and holds the original broadcast using goAsync for at most three seconds. Once the runtime exists, it routes the original press through the same menu/router and aborts only after menu display succeeds. Timeout/failure leaves native delivery available. No outgoing replacement broadcast, root command or automatic Accessibility grant is used. A cold start can add service-start latency before the two-second menu countdown.

Target SDK stays 29. Android 8+ [implicit broadcast restrictions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions) can prevent manifest delivery of KSRC_BUTTON. This does not bypass those restrictions and cannot recover a force-stopped package. Real firmware behavior must be tested; the dynamic receiver is preserved so normal processing is not dependent on manifest delivery.

## Factory Startup Application

Update after inspecting com.hcn.autosetting: its picker uses a fixed XML component list (`/apd/coldstart_launch_app_list.xml`, otherwise `/system/etc/coldstart_launch_app_list.xml`). Label/icon changes cannot add a new entry. Selection uses Settings.System `coldstart_auto_launch_app`; the boot consumer is not identified and shell changes are unverified. Earlier notes below describe the 0.1.12 investigation state. Next app-side boot path: [DIRECT-BOOT.md](DIRECT-BOOT.md).

LauncherActivity already had MAIN/LAUNCHER. Version 0.1.12 explicitly adds its label and icon, which may help a picker inspecting ActivityInfo instead of inherited ApplicationInfo. This does not register an OEM whitelist entry and does not guarantee list membership.

AutoSource does not contain the factory application's picker. Its bundled OEM library contains startup_navigator, but this is not evidence of the Startup Application picker contract. The factory-settings APK is required to determine the actual query, metadata, category or hardcoded allowlist. No guessed factory key or global setting is changed.

## Test

Install over the previous APK. Open once, keep auto-start, handling and HCN enabled and Overlay granted. Reboot without force-stop, do not open CarMode, then press MODE once. A successful cold path opens our first card, not the second; wait for camera launch. Repeat two quick presses for Music and three for Maps. Verify the same sequences with the runtime already running and after swiping away the UI.

Check the Factory Startup Application list after installation. If CarMode is absent, supply the factory-settings APK; do not alter unrelated vehicle settings.

Export diagnostics after the test. Relevant events: mode_wake_received, mode_wake_completed, mode_wake_timeout, mode_wake_error and runtime_started reason mode_wake. Absence of wake events means no recorded manifest delivery, not proof of a specific OEM restriction.
