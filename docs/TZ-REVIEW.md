# Requirements review after the first MVP

This is the English version of the original review, with historical observations clearly separated from the current HCN/Home implementation. See [specification](plan.md) and [README](../README.md) for current scope.

## Clarifications incorporated into the specification

A single MODE button controls cyclic selection. The first press selects Camera; further presses advance and restart the two-second progress bar. Silence confirms the current card. Repeated MODE no longer closes the popup, and the old ten-second dismissal rule is obsolete. Last selection is not remembered. No special reverse-gear policy is required. Startup means background readiness. Builds use Maven.

## Original uncertainty: repeated MODE presses

The learned-KeyEvent MVP can record a key code and process a sequence of presses. Requesting Accessibility key filtering does not prove that the OEM forwards a particular steering button through Android input. [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent))

Original window-based Strategy B is insufficient by itself: opening a carousel can be a single event, while later MODE presses change the existing window internally. Treating every TYPE_WINDOWS_CHANGED as a new press would incorrectly advance on CarMode's own windows and unrelated apps. The first APK collected events but did not automatically recognise/dismiss stock menus with Back.

TYPE_WINDOWS_CHANGED requires FLAG_RETRIEVE_INTERACTIVE_WINDOWS and canRetrieveWindowContent. Version 0.1.0 did not read UI nodes; 0.1.1 added bounded node metadata for `com.hcn.autosource` only, without arbitrary text or other app trees. [AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_RETRIEVE_INTERACTIVE_WINDOWS)

The initial photo showed a Radio/Bluetooth/A2DP/Music/Video carousel and 360/ZLINK5 shortcuts, but did not establish the owning window or control route. Initial diagnostics showed connected Accessibility, zero KeyEvents, two `com.hcn.autosource / android.widget.RelativeLayout` appearances and available ZLink/camera targets. MODE travelled from the steering wheel through CANBUS. Additional selected/focused/scrolled/clicked/content-change events and OEM node polling were added for investigation, not interpreted as physical presses. [AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent)

Subsequent APK analysis found the ordered `android.intent.action.KSRC_BUTTON` route. Plan A uses that route directly; automatic window interception remains unimplemented. A zero KeyEvent count therefore does not invalidate working HCN MODE.

## Startup and actual action results

Android binds Accessibility only after manual user approval; BOOT_COMPLETED cannot grant it. Cold reboot and head-unit sleep are different tests.

Android 10 restricts background Activity launches, with exceptions including certain overlay/Accessibility cases. Firmware can behave differently. A startActivity call without an exception is only a launch request, not evidence of the intended screen. Events use `launch_requested` and `foreground_confirmed=false`. [Background Activity starts](https://developer.android.com/guide/components/activities/background-starts)

Boot/Direct Boot attempts did not establish reliable startup on the tested firmware. The later user-selected Home proxy did. Its retained task and OEM freeform-hint routing preserve the native app grid; the user confirmed working startup and acceptable navigation. This is a device result, not a portable Android boot guarantee.

## ZLink, cameras and removed automation

An exported Activity is only a launch candidate; additional parameters or external state may be required. Camera/ZLink operation must be tested on the device.

Opening ZLink does not prove Music or Google Maps selection inside Android Auto. Do not substitute tablet Maps for that requirement. The original Radio → ZLink Music transition remains an observation, not a documented OEM API.

The original Direct and Hybrid proposals had the same observable behaviour: direct launch plus automation broadcast. Early versions offered Direct and MacroDroid. Earlier paired ACTION_SELECTED/action-specific broadcasts shared a request_id; subscribing one macro to both could execute twice.

Since 0.1.9, these tablet automation modes/broadcasts are removed. Native launches preserve assignments. The phone music HTTP action was clarified and added separately in 0.1.17, default off. A phone receiver is still needed; no tablet MacroDroid dependency is restored.

## Navigation Proxy and acceptance boundaries

Navigation Proxy remains deferred. Intent filters do not guarantee inclusion in an OEM navigation picker; real intents and CarSettings selection criteria must be inspected rather than treating proposed strings as verified firmware contracts.

The initial MVP acceptance covered installation, menu/timer, learning an available key, settings persistence, app launch, then-existing broadcasts and diagnostic export. Current operational acceptance includes HCN input, native app launch and Home startup/grid handoff. The broader original internal-ZLink/navigation integration is still not complete.

Do not confuse successful Maven tests/packaging with confirmed MCU, audio-routing, phone-playback or ZLink capabilities.
