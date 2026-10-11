# CarMode Bridge — specification and implementation plan

This English specification preserves the original goals and investigation routes, while separating them from the implemented product. Current application version: **0.1.22 / versionCode 23**. The README describes operational behaviour; historical proposals below are not claims of shipped functionality.

## Current implementation and device results

- **HCN Plan A is implemented:** receive the OEM MODE broadcast and show our own menu. Plan B, modifying AutoSource resources/code, remains deferred. These Plan A/B names are separate from the original input strategies A/B/C in section 4.
- Since 0.1.8, a separate foreground runtime owns HCN handling and the overlay. Accessibility is optional, not a dependency of the working HCN path.
- Since 0.1.9, destination apps launch directly without MacroDroid on the tablet. Defaults are `ivicar.cn.ivicaravm` for Camera and `com.zjinnova.zlink` for Music/Maps. Explicit saved assignments are preserved.
- Ordinary boot and Direct Boot did not establish reliable startup on this firmware. The 0.1.14 log contained no boot delivery; runtime began after manual opening at approximately 93 seconds uptime.
- In 0.1.15, the user-approved Home proxy established startup; the user confirmed it worked.
- The 0.1.18 all-apps forwarding fix alone did not solve the gesture bug. Logs showed MAIN/HOME and an existing settings-task resume, not ALL_APPS. The original Home did not exhibit the bug.
- The 0.1.19 retained Home task, TASK_ON_HOME handoff and fresh freeform hint restored the OEM app grid; the user confirmed success with a stutter.
- The 0.1.20 warm-return optimization retained that architecture and removed avoidable waits/disk work; the user confirmed acceptable behaviour.
- Version 0.1.21 moved APK tools to Archive and hid explanatory text behind non-blocking help controls. Version 0.1.22 removed the obsolete successful-ZLink-launch Toast.
- Since 0.1.17, an opt-in HTTP GET restores the phone music prompt request from the original Music macro. This needs the phone server; receiving the request and starting playback are separate outcomes.
- Factory Startup Application uses a fixed XML list, not normal launcher labels/icons. Neither CarMode installation nor the startup switch adds an entry. Engineering-console experiments did not establish autostart; further shell investigation was deferred at the user's request.
- The static [OEM key catalogue](HCN-KEY-CATALOG.md) covers 93 declared commands. It is not a verified physical steering-button map.

See [HCN plan](HCN-MODE-PLAN.md), [Home proxy](HOME-PROXY.md), [native actions](NATIVE-ACTIONS.md), [Direct Boot](DIRECT-BOOT.md) and [Music HTTP](MUSIC-HTTP.md).

## 1. Project goal

Develop a native Java Android app replacing the stock popup invoked by the steering-wheel MODE button in a Toyota Camry XV40 (2007).

Original goals:

1. Prevent the stock MODE menu where technically possible.
2. If prevention is impossible, investigate detecting it and displaying a replacement above it.
3. Launch a camera app, ZLink Music and ZLink Google Maps.
4. Execute selected actions directly without third-party automation on the tablet.
5. Investigate registration as a navigation app so it can be selected instead of an absent ZLink entry.
6. Work without root, ADB or system-partition modification.

The current route uses ordered OEM broadcast interception, not automatic window-based replacement. Navigation-app registration and internal Android Auto screen selection are deferred.

## 2. Target platform

| Parameter | Value |
| --- | --- |
| Vehicle | Toyota Camry XV40, 2007 |
| Android | Android 10, API 29 — confirmed by the user |
| Processor | UIS8581A |
| MCU | ZA001-23.12.22_409 |
| Firmware | ZA_2026.03.25.15_6_4465 |
| CANBUS | XinChi/BaoShiJie |
| CAN profile | Toyota/Lexus → ALL_XC01 |
| Display | 1280 × 720 |
| Memory / storage | 8 GB / 128 GB |

The advertised Android 15 label does not match the actual Android version.

Technologies: Java, Android SDK, Maven APK packaging without Gradle, native Android UI/resources, WindowManager, Broadcast Intents and optional AccessibilityService. Do not use root, Xposed, LSPosed or firmware modification in the primary implementation.

## 3. User interface and selection contract

Create a compact, dark, centred popup for a landscape car screen, named **CarMode**.

The settings page offers **EN / ru**. English is the default independently of Android's language. Translate settings, messages, dialogs and popup labels; keep technical broadcast names and JSON field names unchanged.

Three cards, always in this order:

1. **Camera:** camera icon; open the assigned 360/AV-IN app. Do not simulate reverse gear.
2. **Music:** music icon; open the assigned app, ZLink by default. The historical stock Radio → ZLink/music behaviour is an observation to investigate, not a known API. The optional phone HTTP action is separate from Android Auto screen selection.
3. **Maps:** navigation icon; open the assigned app, ZLink by default. Do not represent launching Maps on the tablet as selecting Maps inside Android Auto.

Menu rules:

- The first MODE press opens the popup and selects Camera.
- Each subsequent press cycles Camera → Music → Maps → Camera.
- Every press starts a fresh two-second countdown.
- Two seconds without another press closes the popup and executes the current selection exactly once.
- A smoothly shrinking top bar shows elapsed countdown progress without numbers; MODE refills it.
- All three square cards show their icons; colour/border highlights the selected card.
- Selection uses MODE only, without Back/Next/Confirm buttons or touch selection. System dismissal cancels selection.
- Recognised held DOWN repeats do not repeatedly advance cards; a DOWN/UP pair is one press.
- Every opening starts at Camera. Do not persist the last card or an unfinished countdown.
- Special cancellation/hiding on reverse gear is not required.

Current settings expose only three app-pickers, without launch-test buttons, manual component entry or a separate all-Activity picker. Existing explicit components remain usable; clearing an assignment restores its default.

## 4. Stock MODE interception: original investigation strategies

This is the central integration problem. Do not silently switch to an unverified strategy, disable system packages or equate window changes with physical button presses.

### Strategy A — Android KeyEvent

Provide an AccessibilityService with `FLAG_REQUEST_FILTER_KEY_EVENTS`, `canRequestFilterKeyEvents=true` and `onKeyEvent()`.

Requirements:

1. Record available hardware events.
2. Learn a candidate MODE key code.
3. Require the user's confirmation before assigning it.
4. Consume the assigned DOWN/UP pair, handle repeats/holds, and open the custom menu.
5. Leave unrelated steering keys unchanged.

Implemented as an optional legacy input path. The head-unit logs did not deliver MODE as a normal Accessibility KeyEvent, so HCN is the operational route.

### Strategy B — OEM-window detection

Original proposal if no KeyEvent arrives:

- Observe TYPE_WINDOW_STATE_CHANGED and TYPE_WINDOWS_CHANGED.
- Identify the stock window using package/class and accessible UI structure.
- Use TYPE_APPLICATION_OVERLAY after user approval.
- Attempt Accessibility dismissal only after proving it does not close other apps.
- Prevent recursive detection, duplicate opening and flicker.

**Not implemented as an automatic replacement strategy.** Passive OEM-window diagnostics exist. One carousel appearance does not establish visibility of later presses or justify interpreting arbitrary window changes as MODE.

### Strategy C — OEM integration

Investigate the package owning the menu, its exported components/intents, broadcasts, services and factory key assignment. Use native remapping where verified.

Do not automatically uninstall, force-stop or disable system applications. If a window is outside usable Android mechanisms, document the limitation and investigate another invocation route.

**Implemented HCN route:** `android.intent.action.KSRC_BUTTON` is handled by a priority-999 dynamic receiver in ModeRuntimeService. The menu must display before aborting ordered delivery. Missing permissions, passive tests, learning, malformed/non-abortable input or failures preserve native delivery. `com.hcn.autokey` is logged only. Unknown incoming extras cannot launch arbitrary components.

The fallback manifest receiver and Direct Boot paths remain present but are not substitutes for the device-confirmed Home startup workaround.

## 5. Direct actions without tablet automation

Since 0.1.9, each confirmed selection requests exactly one target Activity launch. Resolve default launcher Activities through Android; saved explicit assignments take precedence.

Removed: DIRECT/MACRODROID execution-mode selectors, targeted automation broadcasts, target-package settings, Intent-copy controls, broadcast tests and MacroDroid-specific state probes. Migrate away from old mode settings without losing assignments.

Do not reproduce stock-app detours, two Back presses, foreign-app closure, audio-stream zeroing or disabled experiments. These were workarounds around stock launches, not requirements for the new direct route.

The original unknown HTTP request was initially excluded. The user later clarified its phone music purpose; version 0.1.17 added the opt-in, bounded Music HTTP action. This does not reintroduce MacroDroid on the tablet.

Record launch requests and errors honestly. A successful startActivity call is not proof of foreground display, selected Android Auto screen or audio playback.

## 6. Integration with installed apps

AppResolver must identify installed candidates, resolve launcher/exported Activities, check accessibility of a target, preserve saved configuration and report unavailable targets. Defaults originate from the supplied macros, not invented package names.

The original design proposed selection of specific exported Activities and manual components. The current simplified UI uses app selection only; saved explicit components still work.

A future ZLinkController could support verified internal navigation, timeouts and cancellation on disabled handling. Do not assume a public Music/Maps API or add unverified Accessibility scripts. Until a contract is confirmed, opening ZLink is the supported behaviour.

## 7. Deferred Navigation Proxy proposal

Problem: ZLink is absent from the OEM navigation-app picker.

Original proposal: an exported `NavigationProxyActivity`, labelled **ZLink Navigation**, investigating MAIN/LAUNCHER, MAIN/APP_MAPS, VIEW with `geo:`, and a navigation intent with `geo:`.

Proposed flow:

1. Invoke a verified ZLink → Maps route.
2. Forward a destination only through a confirmed supported interface.
3. Do not imply a route was created if destination forwarding is impossible.

The original proposal also included `com.carmode.bridge.NAVIGATION` automation broadcasts; tablet automation broadcasts were subsequently removed from product scope. Do not reintroduce them without a separately approved design.

Test actual inclusion under CarSettings → General → Navigation App Selection. Intent filters alone do not guarantee OEM inclusion; criteria may use a fixed list or allowlist. **This proxy is not implemented.** The working HomeProxyActivity is a different feature.

## 8. Diagnostics without ADB

The user should diagnose issues and share evidence without a laptop or repeated shell commands.

Expose:

- Overlay permission, Accessibility permission versus actual connection, and runtime/HCN state.
- Available KeyEvents and key codes.
- OEM window package/class and bounded structural metadata when accessible.
- The input mechanism used and selected app/component.
- Action requests, errors and recognised intents.
- Home-proxy status and navigation/lifecycle evidence.

Provide start/stop recording, confirmed clear, JSON export, selectable visible logs and clipboard copying. Archive contains guided MODE/no-press/other-button/USB-or-Bluetooth-keyboard tests and APK tools.

Persist session/scenario boundaries, export-time settings, completeness counters, lifecycle callbacks and boot history. Do not collect unrelated messages, passwords or arbitrary app-tree text. Do not assert causal relationships from timestamps alone.

Java crash recovery must save a report before termination and offer sharing/copying at the next launch without requiring diagnostics to have been enabled.

See [Diagnostics](DIAGNOSTICS.md).

## 9. Settings and reliability

Implemented operational settings include MODE/HCN enablement, three destination assignments, EN/ru, startup, optional Music HTTP and diagnostics.

Reliability requirements:

- Separate Activity lifetime from the foreground MODE handler.
- Recents removal must not intentionally stop Accessibility or the runtime.
- Compare secure Accessibility enablement with actual service connection; retain callback evidence.
- Do not automatically issue Accessibility approval or secure-setting writes.
- Startup enabled by default means background readiness, not automatic permission approval.
- On this firmware, use the user-selected Home proxy to establish startup and hand off to the original launcher.
- Guard Home handoff loops and expose recovery/rollback.
- Cancel unfinished menus appropriately; do not preserve previous selection.
- Preserve settings on update and use consistent device-protected storage.
- Record failures and bounded timeouts.
- Offer a quick return to native MODE and the original Home.

Do not modify CANBUS, MCU configuration, transmission settings, reverse-camera functions or other vehicle systems. No special reverse-window priority policy is added.

## 10. Development stages and status

1. **Base APK — implemented:** Maven/Java, popup/countdown, settings, legacy key learning, diagnostic export, direct app launch and build guards.
2. **MODE investigation — operational HCN path found:** passive input/window diagnostics, APK analysis and device confirmation. Other buttons remain research.
3. **Menu replacement — implemented:** cyclic clicks, reset, exactly-once timeout and ordered-broadcast interception. Continue hardware regressions for repeats and unrelated keys.
4. **ZLink/camera launch — implemented at app level:** defaults and assignments; optional phone Music HTTP. Internal Android Auto Music/Maps selection is deferred.
5. **Navigation-app registration — deferred:** investigate real OEM selection criteria and supported destination interfaces.
6. **Integration — working target-device reports:** Home startup and native grid handoff confirmed by the user. Continue update, sleep/wake, permissions, sound and rollback checks.

Modified AutoSource Plan B is deferred, not an automatic fallback.

## 11. Acceptance criteria

For the operational product on the target head unit:

1. The ordinary APK installs without root.
2. Steering MODE opens the custom menu and each real press advances once.
3. The stock menu is suppressed on the supported route; unsupported delivery remains native.
4. One/two/three presses launch Camera/Music/Maps after two seconds; each new session starts at Camera.
5. Destination launches do not require MacroDroid on the tablet.
6. Startup through the chosen Home proxy works after full reboot without manual settings opening.
7. Stock Home, Back and the installed-app grid remain usable.
8. Other steering buttons and reverse-camera functions are not intentionally changed.
9. Diagnostics and Java crash reports can be inspected/copied/shared without ADB.
10. The user can disable MODE handling and restore the original Home.

The original broader acceptance also required a functioning Navigation Proxy and verified internal ZLink scenarios. Those are explicitly deferred and are not claimed complete. Build success does not replace hardware acceptance.

## 12. Deliverables and repository policy

Provide the Java source, Maven/Android SDK build, an installable APK, English README and documentation, installation/permission instructions, app-assignment/rollback guidance, honest implementation limits, relevant observed broadcast contracts and automated regression tests.

Track source, tests and Markdown guides in Git. Store exported APKs/macros, raw diagnostics and reference images in ignored `docs/local/`. Build scripts remain local in ignored `scripts/` by user request and must be copied separately to build a fresh clone. Also ignore signing material, generated builds and uploaded attachments. Preserve local evidence; ignoring a file is not deleting it. Remote setup, commits and publishing require a separate request.

Development priority remains: **MODE interception → custom menu → native launch → verified ZLink actions → Navigation Proxy**.

Work iteratively. Do not invent OEM APIs, components or MCU capabilities. Separate static evidence, hypotheses, automated checks and user-confirmed device behaviour.
