# Optional Home proxy — 0.1.23

Current status (0.1.23): OEM mode remains the default and retains the user-confirmed startup from 0.1.15, restored stock grid from 0.1.19 and acceptable navigation from 0.1.20. Optional Camry Home renders in the same HomeProxyActivity; see [Camry architecture and acceptance](CAMRY-HOME.md). New features are off until explicitly enabled. Sections labelled 0.1.15–0.1.18 document superseded experiments, not instructions to restore finish/transparency behaviour. No claim is made for every device, privileged Recents integration or all vendor gestures.

## Warm handoff optimization (0.1.20)

The user confirms 0.1.19 opens the OEM grid correctly, with a visible stutter. Version 0.1.20 keeps the retained opaque Home/task flags and recovery safeguards unchanged. An already fresh freeform hint schedules the foreground return on the next handler turn without the mandatory 120 ms pause. If the hint arrives while a foreground return is pending, it replaces the fallback callback with an immediate one. Missing or expired hints retain the 120 ms fallback; broadcasts never launch over another app. Pause, explicit navigation, destruction and recovery still cancel pending callbacks.

Only the initial handoff of an Activity writes the synchronous boot history and full Home snapshot. Warm handoffs keep lightweight navigation events and forwarding details; errors still persist to boot history. This avoids several disk commits and role/package queries before each warm launch. Initial startup/recreated Home evidence remains available. OEM rendering time is not controlled by this change, and smoothness must be compared on-device. Test repeated grid opening from Home and other apps, normal app launch/Back, and reboot/MODE; export the log if navigation regresses.

## Retained Home experiment (0.1.19)

The 0.1.18 attachment records MAIN/HOME delivery followed by resumption of the existing settings task, not ALL_APPS delivery or a new settings Intent. The user confirms original Home has no such bug. In the supplied OEM code, Workspace.workspaceScroll and onStartStateTransition and Launcher.mCarHomeAllAppsReceiver can call moveTaskToBack(false) when ro.auto.homefreeform is true. This is a plausible mechanism, not confirmation that this property/branch executed on the tablet.

Version 0.1.19 retains the proxy Home Activity after forwarding instead of finishing it. The OEM launch adds TASK_ON_HOME alongside NEW_TASK and NO_ANIMATION. Android documents this flag for newly launching tasks; reuse of an existing OEM task and the vendor's freeform handling still need device verification. An opaque dark Home window replaces the translucent one so a foreground Home task does not merely reveal the previous app. This can introduce a brief dark handoff; it is not a custom app drawer.

While alive, the Home Activity observes action.hcn.launcher.freeform.move. The broadcast only stores a 1.5-second hint and logs it; it never starts an Activity in the background. On resumption after a successful handoff, the proxy waits 120 ms for the OEM signal, then forwards ALL_APPS when fresh, otherwise normal HOME. Explicit Home navigation clears stale hints. More than two rapid return-driven handoffs (less than 2 seconds apart) expose recovery controls rather than looping forever. Receiver/pending callbacks are cleaned up on destruction. Recovery exposes Launcher retry, original Home settings and CarMode settings. A default-Home change stops automatic return forwarding. MODE runtime and menu selection remain independent and unchanged.

Test: select CarMode as Home if original Launcher was selected for comparison; install 0.1.19 over the existing app. Start logging, press Home, swipe/open the OEM grid from both CarMode settings and another app, open an app from the grid, check Back/Home, then reboot and test MODE without opening settings. Export JSON on failure, including any recovery screen or dark-screen delay. New navigation stages include home_retained, oem_freeform_move, resume/pause/destroy and return_HOME/ALL_APPS/RECOVERY. No claim is made that this restores privileged/system recents integration or every vendor gesture.

References: [TASK_ON_HOME](https://developer.android.com/reference/android/content/Intent#FLAG_ACTIVITY_TASK_ON_HOME), [moveTaskToBack](https://developer.android.com/reference/android/app/Activity#moveTaskToBack(boolean)).

The following sections describe the earlier forwarding implementations and their history; 0.1.19 supersedes their finish/transparency behavior.

## All-apps navigation investigation (0.1.18)

The attached 0.1.17 diagnostics contain three Home-proxy forwarding requests followed by settings Activity resumes, with no recorded MODE or app-launch events in that recording. They do not record the incoming navigation action, so they cannot establish whether a swipe sent ALL_APPS, MAIN/HOME, or simply resumed an existing settings task.

Inspection of the supplied OEM Launcher.onNewIntent confirms that UIS8581 recognizes `android.intent.action.ALL_APPS` separately from MAIN; MAIN resets the launcher to its normal workspace. The previous proxy always sent MAIN/HOME. Version 0.1.18 forwards ALL_APPS (including legacy MAIN + `extra.show_page=allapps`) explicitly to the known OEM component. It no longer adds RESET_TASK_IF_NEEDED, avoiding an explicit request to reset the OEM task. Normal Home remains MAIN/HOME. The proxy exposes a separate ALL_APPS/DEFAULT filter; known navigation requests accidentally addressed to either app entry are routed through the proxy. Ordinary icon/notification launches and plain settings-task resumes are not hijacked.

Forwarded intents are rebuilt with only the recognized action/page, HOME category when appropriate, explicit component, NEW_TASK and NO_ANIMATION. Arbitrary extras, data and incoming flags are not propagated. `navigation_entry` records entry component, lifecycle stage, task ID/root, incoming action/categories/flags/component and allowlisted show_page; proxy-forward events also include the outgoing intent. These are diagnostic events, not proof that the OEM screen became visible. No raw gesture injection, privileged launcher API, Home-role change or service-lifecycle change is introduced.

Install 0.1.18, enable logging, press Home and swipe up to the installed-app grid; repeat after opening an unrelated app and after opening CarMode settings. Check Back and reboot/MODE as regressions. Export the log if settings or the previous app returns. Gesture behavior on the head unit is still unverified; if firmware only resumes a task without delivering a navigation intent, this routing fix alone will not handle that path.

Intent semantics: [Android Intent reference](https://developer.android.com/reference/android/content/Intent).

User reports that the 0.1.15 Home path works, with a brief black-screen handoff on Home/Back. Version 0.1.16 gives only HomeProxyActivity a translucent, transparent, undimmed theme without a starting preview; disables forwarding/finish animations; and reuses a live runtime instead of requesting another service start. Crash gating, explicit OEM component, default Home consent and rollback remain unchanged. Recovery still has an opaque page. Actual firmware flicker/latency reduction requires device verification; no claim is made that the OEM launcher itself cannot redraw.

## Why this experiment

The supplied 0.1.14 diagnostics show no recorded boot delivery on the latest reboot. The runtime starts after UI opening at about 93 seconds uptime. Settings migration succeeded. Direct Boot did not establish autostart on this firmware.

The supplied launcher APK is `com.android.launcher3`, version `40.2602091651`, with a HOME Activity `com.android.launcher3.Launcher` and shared system UID declaration. SHA-256: `023B59E5DB20133CFB42D37F1420A296DD2EC9E12D6ADA8AE4261BC8823C5785`. Inspection of the main code and bundled CAR_HOME_V5 module did not find the Startup Application boot consumer. ZLink launch in the bundled dock module is a button action. A theme package can supply additional code; jadx reported nine decompilation errors in the main APK, so this is not proof of absence across all OEM software.

## Implementation

HomeProxyActivity is a separate MAIN/HOME/DEFAULT component, without LAUNCHER category, isolated task affinity and excluded from Recents. Selecting it as Android Home is optional and must be approved in Android UI via RoleManager ROLE_HOME, with Home settings as fallback. Installation does not select it automatically. The selection button first verifies the exact OEM Activity exists, is enabled, exported and accessible.

On Home invocation, the proxy requests existing foreground runtime startup when auto-start is enabled and crash recovery is not pending. Existing runtime policy still controls disabled MODE/diagnostics behavior; no preferences are forced on and manual activation is not set. It then explicitly opens `com.android.launcher3/com.android.launcher3.Launcher` and finishes. It never forwards to implicit HOME, avoiding self-resolution loops. Runtime startup failure does not block forwarding. OEM launch failure leaves a recovery screen with Home settings and CarMode settings buttons. Existing menu input/cycling, native app assignments, boot receivers and Accessibility behavior are unchanged.

The proxy affects every Home invocation, not just boot. The brief handoff may be visible, and task/navigation behavior must be tested on the head unit. It cannot promise that firmware honors the Android default Home selection or preserves a running service. It does not add CarMode to Factory Startup Application, change shared UID, grant permissions, patch the launcher, issue shell commands or edit system files.

Diagnostics include `home_proxy` state (resolved default Home, role availability/ownership, OEM availability) and events `home_role_requested`, `home_role_result`, `home_proxy_entered`, `home_proxy_forward_requested`, `home_proxy_error`. `boot_history` includes entry and forwarding stages even with ordinary diagnostics off. A successful startActivity request does not prove foreground display.

## Tablet test and rollback

1. Install 0.1.15 over the existing app; open CarMode once. Enable auto-start, MODE handling, HCN and Overlay. Accessibility is optional.
2. On Current, press “Choose CarMode as Home” and approve CarMode Bridge in Android. If the dialog/selection is blocked or unavailable, stop and report it; do not assume installation alone activates the proxy.
3. Press Home: the familiar OEM screen should appear. Reopen CarMode and confirm the Home status names `com.carmode.bridge.HomeProxyActivity` (green check on the selection button).
4. Fully reboot. Do not open CarMode through its icon or Recents. Test one, two and three MODE presses.
5. Afterwards open CarMode and export JSON even on failure. Also check normal Home/Back and Recents behavior.
6. Rollback: press “Home settings: restore the original launcher” and select the original Launcher instead of CarMode. This button opens Android settings; it does not silently change the default. Restore Home before uninstalling or force-stopping CarMode. If forwarding fails, the proxy recovery screen exposes the same settings action.

Standard role API: [Android RoleManager](https://developer.android.com/reference/android/app/role/RoleManager).
