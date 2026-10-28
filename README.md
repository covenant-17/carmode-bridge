# CarMode Bridge

A native Android app that replaces the stock MODE menu on aftermarket head units with HCN OEM software, tested on **UIS8581A running Android 10 / API 29**. It receives steering-wheel MODE presses through HCN OEM broadcasts rather than relying on standard Android key input. This input path works **without Accessibility access or root**, and leaves system APKs unchanged.

Current application version: **0.1.50**, Android versionCode **51**.

## Current behaviour

- A centred three-card menu: **360 Camera → ZLink Music → ZLink Google Maps**.
- The first MODE press selects Camera. Each further press cycles to the next card and restarts a two-second countdown.
- Two seconds without another press launches the selected app once and closes the menu. A shrinking bar shows the remaining time without numbers.
- Every new menu starts at Camera. Cards cannot be selected by touch; recognised held-key repeats do not advance selection.
- Camera, Music and Maps each have an app picker. Defaults are `ivicar.cn.ivicaravm` for Camera and `com.zjinnova.zlink` for Music/Maps. Existing explicit Activity assignments take precedence.
- Optional HTTP(S) GET after Music launches, disabled by default, with an editable phone-server address.
- **EN / ru** language selection, with English as the default.
- **Current** contains operational settings; **Archive** contains legacy input tests, detailed diagnostics and APK export tools. Help is available through inline ⓘ buttons: tap for a non-blocking Toast or long-press for a tooltip.
- Diagnostic JSON export, selectable/copyable logs and persistent Java crash reports.
- Successful MODE launches are silent; launch failures still display an error.
- Optional **Camry Home** uses the existing dark blue / turquoise UI with square panels, Android Auto, Camera, apps and settings. Experimental window mode reserves the full usable height for ZLink or the configured camera app, without a heading, date, surrounding outline or bottom launch buttons. Camera selections through CarMode use that area and retain the sidebar; its first button switches back to ZLink while cameras are shown. Native Home returns to ZLink. External/reverse-camera activation remains controlled by the firmware; actual camera video scaling and task resizing require device verification. OEM Home forwarding remains the default.
- **All apps in Home window** is a separate Current switch, off by default. It applies to external apps launched through CarMode Apps, pins, MODE/Dock assignments and the Android settings rail button. Enabling it enables Home/window mode; CarMode settings and recovery remain ordinary app screens. Some apps ignore window bounds, and launches outside CarMode are not intercepted.
- **Auto-launch cameras on startup** is separate from **Auto-launch Android Auto on startup**. With both on, the first eligible Home entry per Android boot requests cameras in the Home rectangle first, then requests ZLink after a fixed 1.5-second delay. Camera startup enables Home/window mode and disables the competing boot-camera return observer. Camera-only startup has no timed ZLink handoff. The delay is not a readiness signal and does not disable independent OEM startup.
- **Return Home after first boot-time camera launch** is a separate opt-in Current switch. It watches foreground events for the first two minutes of Android boot using Usage access and returns to Home with ZLink once if the configured camera appears. It cannot distinguish auto-start from reverse gear during that initial window. Manual camera selections through CarMode cancel the return for that boot; later camera appearances are left alone.
- An independent **Dock session** offers Camera, Music, Home, Apps, Restore OEM, CarMode settings and Hide. Camry mode shows it whenever neither Home nor Apps has a visible sidebar, including over settings and fullscreen external apps. Manual sessions have a separate switch, off by default. Music uses the MODE Music assignment (ZLink by default), including optional Music HTTP. Dock does not require Accessibility.
- Five separate Back presses within five seconds on Camry screens restore the previous CarMode behaviour, preserving MODE settings. System Back, Home and Recents are never hidden or intercepted by Camry.

Music and Maps currently **open their assigned app**. They do not automatically select a Music/Google Maps screen inside Android Auto.

## How MODE and startup work

The working input path is steering wheel → CANBUS/MCU → OEM `android.intent.action.KSRC_BUTTON` broadcast → CarMode's foreground handler. CarMode displays its menu and interrupts further **abortable ordered** delivery only after the menu opens successfully. The stock AutoSource APK is not patched or disabled.

HCN handling does **not** require Accessibility or MacroDroid. An optional Accessibility service remains for learned Android KeyEvents and OEM-window diagnostics; it is not the primary input path on this head unit.

Ordinary boot/Direct Boot receivers did not establish reliable startup on the tested firmware. The working workaround is an optional **Home proxy**:

1. The user selects CarMode as Android's default Home app.
2. Android opens the proxy, which brings up the MODE runtime when enabled.
3. With **Home mode: Camry** off, the proxy forwards to the original `com.android.launcher3/com.android.launcher3.Launcher`. With it on, the proxy displays Camry Home instead.
4. In OEM-forwarding mode, the retained Home task and OEM freeform signal support returning to the stock installed-app grid. In Camry window mode, explicit Home entry requests the default ZLink window once; Apps opens as a separate opaque screen with the same sidebar.

Earlier device observations confirmed the OEM-forwarding startup path. Current window-mode behavior, release-specific tests and limitations are recorded in [Camry Home](docs/CAMRY-HOME.md) and [Release history](CHANGELOG.md). OEM-forwarding results do not establish acceptance of every newer window feature.

## Target device

| Parameter | Value |
| --- | --- |
| Vehicle | Toyota Camry XV40, 2007 |
| Actual Android version | Android 10 / API 29 |
| Platform | UIS8581A |
| Display | 1280 × 720 |
| CANBUS | XinChi/BaoShiJie |
| CAN profile | Toyota/Lexus → ALL_XC01 |
| MCU | ZA001-23.12.22_409 |
| Firmware | ZA_2026.03.25.15_6_4465 |

The firmware's advertised Android 15 label is not its actual API level. Application minSdk and targetSdk are both 29; compilation uses SDK 34.

## Build on Windows

Requirements: **JDK 17**, Maven **3.9+**, Android SDK Platform **34**, Android SDK Build Tools **34.0.0**, and PowerShell. SDK components can be installed through Android Studio's SDK Manager; Android Studio is not needed for subsequent builds.

Clone the repository and enter its directory. All required project build scripts are included:

```powershell
git clone https://github.com/covenant-17/carmode-bridge.git
cd carmode-bridge
```

Before producing an APK, increment both the Maven application version in `pom.xml` and Android `versionName` / `versionCode` in `src/main/AndroidManifest.xml`. Never produce a replacement APK under a version already used for a successful build. Then build:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
mvn -B clean verify
```

The SDK defaults to `%LOCALAPPDATA%/Android/Sdk`. Override it if necessary:

```powershell
mvn -B clean verify '-Dandroid.sdk=D:/Android/Sdk'
```

Output: `target/carmode-bridge-<version>-debug.apk`, using the incremented version. The existing 0.1.50 artifact is named `carmode-bridge-0.1.50-debug.apk`.

- `mvn test`: Java unit tests and compiler-stub guard regressions, without APK packaging.
- `mvn package`: tests and APK compilation, alignment and signature validation.
- `mvn verify`: the complete lifecycle through packaging and verification.

Maven invokes AAPT2, javac, D8, zipalign and apksigner through the included PowerShell scripts in `scripts/`; no separate script download or manual invocation is needed. APK packaging uses a fresh private `target/apk-staging/<uuid>` directory rather than trusting IDE-writable `target/classes`. JAR, DEX and signed APK checks reject `Unresolved compilation problem` compiler stubs. The APK is attached to the Maven artifact with classifier `debug`.

Open `pom.xml` as a Maven project in your IDE. Android Studio Gradle Run/Sync is not supported. Use JDK 17: the configured Build Tools/D8 combination previously failed with JDK 21.

Keep `pom.xml` and `src/main/AndroidManifest.xml` aligned. After each successful APK build, complete validation and create a local commit containing the relevant implementation, version metadata and release notes; prefer one commit per version and preserve unrelated work. Do not commit APKs, logs or signing keys, and do not push without a separate explicit request. If a successful build needs an implementation fix, increment both versions before its replacement build. Documentation-only changes do not require an APK or an application version increment.

### Signing

The local debug signing key is stored in `.local/debug.keystore` and is intentionally excluded from Git. Preserve it separately and securely to install future updates over an existing app. A fresh clone may generate a different key and cannot update an installation signed with the previous key.

Never commit keystores or export them along with diagnostic files.

## Install and configure

Perform all setup and testing while parked.

1. Transfer and install the APK. For updates, install over the existing app without uninstalling or clearing data; use the same signing key.
2. Open **CarMode Bridge** once. Choose **ru** if desired; Android system screens and third-party app names use their own language.
3. Grant permission to display over other apps.
4. Enable MODE handling and **Control HCN MODE**. Leave startup enabled for the Home startup path. No key learning or Accessibility approval is needed for HCN.
5. Use the three app pickers to select Camera, Music and Maps if the defaults are absent or unsuitable. Clearing an assignment in the picker restores its default.
6. Use the MODE simulation control to check one, two and three presses. There are no separate app-launch test buttons or manual component-entry controls in the current UI.
7. Choose CarMode as Home through the Current tab and approve the Android selection as the default (Always, where offered). With **Home mode: Camry** off, verify that system Home opens the original launcher. Enable Camry mode separately if you want its Home interface; selecting the Android default and enabling Camry mode are separate steps.
8. Configure the optional window switches below. Fully reboot, do not manually open CarMode, and check the steering-wheel MODE button. Test native Home/Back, Apps, camera selection and sleep/wake separately. A recurring Android Home chooser means a default has not been retained; check Android Home settings rather than repeatedly choosing Just once.

The startup switch alone does not select Home or obtain an OEM whitelist entry. Android 10 has no ordinary runtime permission dialog for BOOT_COMPLETED. CarMode does not add itself to the factory **Startup Application** list, which uses fixed OEM XML entries.

For the alternative learned-key route, disable HCN, grant Accessibility manually, start learning in Archive, press MODE and confirm the observed key. Learning is passive and does not consume input.

## Camry Home, Dock and ZLink startup

Window-mode Home and Apps share the same right-hand controls and expandable shortcuts. The initial five cells gain an additional empty cell whenever all existing positions are filled. The initial five cells preserve empty positions until filled. After expansion, removing a shortcut compacts the remaining apps and retains an empty tail, with at least five cells. Only shortcuts scroll; the primary controls remain fixed and no scrollbar is drawn. Tap an empty plus to select an app in the existing tile grid; selection pins it without launching it. Filled cells show only the app icon and launch on tap, including ZLink Home-only routing. Hold and drag to swap positions or move into an empty cell. Drop on the temporary top-centre trash to remove the shortcut, preserving the installed app; drop elsewhere to cancel. The trash needs the existing display-over-other-apps permission so it appears above ZLink. Assignments persist across restarts and Camry rollback.

Install the current APK over the existing app using the same signing key; do not uninstall or clear data. Updates preserve existing assignments, pins and feature selections. On a fresh install the Camry Home, window, Home-only, all-apps window, boot-camera return, automatic camera/ZLink startup and manual Dock switches default to off.

In **Current → Camry OS**, enable Camry Home if desired. Enabling it checks that the exact stock Launcher3 and Android Home settings are available; it does not silently select CarMode as the default launcher. Use the existing **Choose CarMode as Home** action separately. **Open Camry Home** previews the interface without changing Android Home. The Home mode switch returns to OEM forwarding when off.

**ZLink in Home window (experimental)** reserves the main Home area for an external window and keeps the right sidebar available. Launching the configured camera through CarMode also uses that area while Camry Home and window mode are on. Camera selection works through the rail, Dock, MODE, Apps and pinned shortcuts. While a camera or another app occupies the area, the first rail button opens ZLink. Native Home and Dock Home return through the same Home entry and request the default ZLink window. Camera activation directly by the vehicle is normally left alone; the separate boot-camera return switch below is the opt-in exception. The user reported that manually placing the real camera app in a window allowed later gear changes to reuse that placement; the initial automatic launch can still be fullscreen.

**ZLink only in Home window** enables Camry Home and window mode. ZLink launches through CarMode Apps, pins, MODE and Dock return to Home and request its window, preserving the selected Activity. Automatic restoration on return is limited to one attempt per launch; transient focus changes during phone connection do not rearm it. Explicit Home navigation can request the window again. Apps uses a separate opaque screen so its grid stays above the foreign window. Five local Back presses still provide rollback.

**All apps in Home window** is a separate switch, off by default. It enables Camry Home and window mode and routes external apps launched through CarMode Apps, pins, MODE/Dock assignments and the Android settings rail button into the same area. CarMode's own settings, Apps and recovery remain ordinary screens. The choice persists across restarts. Turning off Home or window mode, or restoring OEM, clears the switch. Turning this switch off alone retains the existing ZLink/camera behavior. It does not intercept launches initiated outside CarMode.

Window placement is a request to Android, not a video capture or an embedded View. Apps or OEM firmware can ignore bounds, retain an existing fullscreen task or handle video surfaces differently. In the 0.1.47 emulator check, Contacts used the Home rectangle while Files ignored the request and remained fullscreen. Real-device behavior must be checked per app.

**Return Home after first boot-time camera launch** enables Camry Home and window mode. To use it, enable the switch in Current, tap **Allow Usage access for CarMode Bridge**, grant that access in Android settings, retain overlay permission and enable device startup. Then reboot. Accessibility is not required. The background runtime samples foreground app events every 500 ms only during the first 120 seconds of Android boot. If the configured camera is the current foreground app and the display is unlocked and interactive, it persists a boot claim and requests Home with default ZLink once. It does not inject Back or stop the camera process. The claim survives process restarts and switch toggles; a new readable Android boot identity permits a new attempt. A camera selected through CarMode consumes the claim without redirecting. Missing Usage access, missing overlay permission, an unknown boot identity or crash recovery prevents the return. Home off and OEM rollback disable the switch.

From 0.1.49, lifecycle pauses are matched to the Activity as well as its package. If lifecycle events do not identify cameras, a secondary UsageStats check accepts only uniquely recent camera usage (at most 2.5 seconds old), newer than lifecycle evidence and other apps, and within the current boot. Stale, tied and future timestamps are rejected. Diagnostic JSON includes `state_at_export.camera_boot_return`; throttled `camry_camera_boot_observation` events record permission/display gates, empty events, foreground evidence and expiry. The 0.1.48 head-unit journal showed the observer expire without a Home request, but did not capture enough evidence to establish which detection/gate failed.

This is a one-time startup workaround, not a reverse-gear detector. It can return Home if the first external camera appearance in those two minutes was caused by reverse gear. It does not repeatedly fight camera launches, and it does not prove that ZLink's phone connection becomes faster. Usage access availability and persistence must be verified on the head unit.

Independent fullscreen ZLink auto-connect remains firmware-dependent. Current Camry settings do not prompt for Accessibility because the head unit was reported to reset its grant on reboot. If the optional legacy service is enabled separately, its ZLink observer waits for three seconds of unchanged fullscreen dimensions and makes at most **one correction per service/settings session**. It then stops correcting; it does not restore its own permission. This observer does not redirect cameras or arbitrary apps. No Android Auto connection-ready signal is currently used, and the effect on phone connection time has not been measured.

**Auto-launch cameras on startup** enables Camry Home/window mode and disables Return Home after first boot-time camera launch. It uses the Camera assignment, while the ZLink follow-up uses Select Android Auto app. The first focused, default Home window entry claims the boot before requesting cameras. Explicit Apps/pin destinations and previews do not start the sequence. Camera startup remains off on update until enabled. If **Auto-launch Android Auto on startup** is also on, ZLink is requested in Home 1.5 seconds after the camera launch request; a failed camera request can still continue to ZLink. With only camera startup on, no timed ZLink follow-up is scheduled. A later explicit Home entry still opens the normal default ZLink window.

The camera sequence is not an Android Auto/camera-ready check. Expected Activity pause during camera launch does not cancel the handoff. CarMode input, Back, explicit Home/MODE/Dock navigation, relevant settings, screen-off and rollback do cancel it. Input inside an external camera app is not globally intercepted. The sequence requests window bounds rather than proving camera rendering, and it cannot stop firmware from independently launching apps again. No Usage access or Accessibility is needed for this direct startup path.

**Auto-launch Android Auto on startup** is independent of the MODE startup switch and defaults to off. With camera startup off, Camry window mode already requests ZLink on each explicit Home entry and skips the separate once-per-boot fullscreen startup path. Outside window mode, it launches from the visible Home Activity after a short UI delay, never from a boot receiver/service. In OEM mode a temporary Camry startup screen hosts the attempt, then returns to OEM on Back or after a failed/cancelled attempt. The selected Android Auto app defaults to `com.zjinnova.zlink`; launch failures leave Home available. This CarMode switch does not control ZLink's own phone auto-connect setting. Maps, AIMP, phone connection and sound routing remain ZLink responsibilities.

A synchronous persisted claim permits at most one automatic attempt for a readable Android `BOOT_COUNT`, including failed launches and process/Activity recreation. User interaction, Back, pause or setting changes cancel pending work. Home/Back do not reset the claim. If boot identity is unavailable, only one unknown-boot attempt is allowed across process restarts; a later readable boot identity allows a new attempt. Sleep/wake or ACC power changes that do not reboot Android are not new Android boots. One launch per physical vehicle power cycle is not guaranteed.

**Allow CarMode Dock** enables manual sessions; **Show Dock for this session** starts one after an explicit camera warning. Closing settings keeps the session alive. Hide collapses to an Open Dock button anchored in the selected one of four corners; expanding returns to a horizontal bottom-centred panel raised 32 dp above the usable bottom edge. Close Dock or the notification action removes it. Position and opacity are adjustable. The bounded overlay does not take key focus or outside touches and fits within the system-managed usable area. Apps lists installed apps; it does not replace system Recents.

In Camry mode, Dock appears automatically when neither Home nor Apps has a visible sidebar, including over settings and external apps. A sidebar remaining visible behind freeform ZLink suppresses Dock; returning to Home or Apps removes it. Transitions between own screens are debounced. Overlay permission and a healthy app state are required; the manual-session switch is independent. Screen-off, lost permission or rollback closes Dock. Wake/unlock rechecks sidebar visibility while the process is alive. Dock is non-sticky, with no boot receiver or global foreground-app polling. Native OEM camera surfaces can still hide overlays or fail to notify normal Activity visibility changes; real head-unit acceptance is separate. No mandatory Accessibility or OEM/MCU modification is added.

## Music HTTP option

Enable the Music HTTP switch only if the phone receiver/server is available and the address is correct. CarMode sends a GET 1.2 seconds after the Music launch request succeeds. It sends nothing on boot, Home, Camera, Maps or a failed launch.

The original default address is local HTTP, not HTTPS. The tablet no longer requires MacroDroid; the phone still requires its receiver. An HTTP success response does not prove music playback. See [Music HTTP](docs/MUSIC-HTTP.md) for network, timeout and privacy details.

## Diagnostics, crashes and APK export

Use ordinary recording to reproduce a live MODE/Home problem, then stop and export JSON. Archive's guided tests are deliberately **passive** and let the stock handler run; do not use them to judge the live replacement.

Logs can be selected or copied to the clipboard. UI refresh is deferred during interaction/selection/scrolling; event recording continues. Clear logs requires confirmation and preserves settings, previously shared files, separate crash reports and boot history.

A caught fatal Java failure is persisted before process termination. The next launch opens recovery with report sharing/copying and an option to open settings with MODE handling disabled. Force-stop, native crashes, ANRs, power loss and severe resource exhaustion may leave no report.

Archive can export the installed AutoSource, ZLink or another selected app's APK. A split package is exported as a ZIP. Only APK files are copied, not private app data; packages are not installed, modified or re-signed. Downloads use Android 10 MediaStore, and sharing uses temporary read-only URI grants.

See [Diagnostics and recovery](docs/DIAGNOSTICS.md) for test procedures, export fields and limitations.

## Rollback and limitations

- **Restore previous CarMode behavior**, available through Current settings, recovery and the expanded Dock's **Restore OEM**, durably disables Camry Home, Dock, camera/ZLink startup, experimental window mode, Home-only routing, all-apps window routing and boot-camera return, cancels pending new work and explicitly opens Launcher3. MODE, HCN, app assignments, pins and the original runtime startup setting are preserved. A recovery page remains behind the launch request; the app does not claim that an accepted request proves OEM Home appeared.
- Five distinct Back presses within five seconds perform that same rollback without confirmation. The first four navigate normally; the root Camry screen stays open to complete the sequence. Held-key repeats do not count. Other input, leaving CarMode or expiry resets the sequence. This is local to CarMode screens; it does not observe Back inside ZLink or other apps. The Dock offers the explicit rollback there.
- **Choose stock Android launcher** first disables new features, then opens Android Home settings for the user to select Launcher3. The app checks the actual default on return, including cancellation. This system escape remains available even when persisting rollback fails. If OEM or settings launch fails, recovery controls remain available. The app icon remains an independent route to settings/crash recovery.
- Before uninstalling: restore the previous behaviour, choose Launcher3 in Android Home settings, press system Home to confirm it works, then uninstall CarMode. New features stay off after process restart/reboot until explicitly re-enabled. The original launcher is never disabled, deleted or modified.
- Disable MODE handling to restore native input behaviour and cancel a pending menu. Disabling only HCN can leave the learned-KeyEvent path enabled.
- To remove the startup workaround, open Home settings and choose the original launcher. **Restore Home before uninstalling or force-stopping CarMode.**
- Accessibility permission is never granted or restored automatically. The foreground runtime improves lifetime but cannot override force-stop or firmware policy.
- Missing overlay permission, non-abortable broadcasts, guided tests, learning or menu failure preserve native MODE delivery.
- Broadcasts are public and are not proof of a physical steering press. Unknown extras never become arbitrary app-launch commands.
- No raw CAN capture, MCU configuration, system APK patching, volume-zero workaround or special reverse-gear exclusion is implemented.
- The 93-command OEM catalogue is static analysis, not a verified physical-button map or a promise of global interception.
- Navigation Proxy, internal Android Auto screen automation and modified AutoSource Plan B remain unimplemented.

## Repository layout

| Path | Purpose |
| --- | --- |
| `src/main/` | Android Java source, manifest and EN/ru resources |
| `src/test/` | Java regression tests |
| `scripts/` | Included Maven/Android build and bytecode-validation scripts |
| `docs/*.md` | Specifications, operational guides and OEM research |
| `docs/local/` | Ignored APKs, device logs, macro exports and reference images |
| `target/` | Ignored build output |
| `.local/` | Ignored signing key and local analysis/tools |
| `.codex-remote-attachments/` | Ignored uploaded diagnostic material |

Source, tests, build scripts and Markdown documentation belong in Git. Raw APKs, device logs, macro exports and reference images are kept in ignored `docs/local/`. Local evidence and signing keys are retained locally, not deleted. This is an independent Git repository rooted in this project directory.

## Documentation

- [Specification and implementation status](docs/plan.md)
- [Requirements review](docs/TZ-REVIEW.md)
- [Home proxy and rollback](docs/HOME-PROXY.md)
- [Camry Home architecture and device acceptance](docs/CAMRY-HOME.md)
- [Native actions and removed MacroDroid workarounds](docs/NATIVE-ACTIONS.md)
- [Music HTTP](docs/MUSIC-HTTP.md)
- [Diagnostics and recovery](docs/DIAGNOSTICS.md)
- [Background lifecycle](docs/BACKGROUND-LIFECYCLE.md)
- [Direct Boot investigation](docs/DIRECT-BOOT.md)
- [Cold MODE wake investigation](docs/MODE-WAKE.md)
- [HCN Plan A and deferred Plan B](docs/HCN-MODE-PLAN.md)
- [OEM key catalogue](docs/HCN-KEY-CATALOG.md)
- [Release history](CHANGELOG.md)

Automated tests validate the covered logic and packaging guards, not physical CAN delivery, OEM rendering, sound routing or phone playback. Device observations and unverified capabilities are documented separately.
