# Changelog

Historical implementation notes. Current operation and setup are documented in [README](README.md). Device confirmation is stated only where reported; earlier experiments are not current guarantees.

## Unreleased

- Translate remaining documentation to English and reorganize README around the current product.
- Document the full 93-command static OEM key catalogue and diagnostic/recovery procedures.
- Initialize an independent local Git repository; include source, Markdown and required build scripts, move device evidence to ignored docs/local/, and ignore signing material and generated output. Document clone-to-APK Maven setup without separate script copying.
- Add a source-level regression guard for English-default settings and MODE labels while preserving an explicit Russian choice.
- These documentation/test maintenance entries originated against 0.1.22; application changes for 0.1.23 are listed below.

## 0.1.41

- Add CarMode settings immediately before Hide in the expanded Dock, using the existing theme icon and the explicit app settings entry. Retain bounded horizontal scrolling on narrow displays.

## 0.1.40

- Ignore initial sticky MODE replays delivered when the runtime receiver registers. A saved boot-time broadcast cannot open the menu and time out to its default Camera choice. Fresh MODE clicks and the manifest cold-wake handoff remain unchanged. Log the initial-sticky flag and show its reason in HCN status. This closes a plausible internal path; it does not establish the cause of the reported head-unit camera launch or suppress native/reverse-camera startup.

## 0.1.39

- Extend Home-only routing to externally launched fullscreen ZLink when the user enables the existing CarMode Accessibility service. Observe only focused application package/bounds, debounce window events, and return via the existing measured Home launch. Bounded ZLink, Apps, cameras and unrelated packages are left alone. Limit correction to three attempts per service/settings session with a two-second cooldown; report exhaustion instead of looping when firmware refuses bounds.
- Show Accessibility availability and a system-settings entry next to Home-only mode. This optional correction does not make Accessibility mandatory for HCN MODE or ordinary CarMode launches.
- Record observed foreground camera package/boot uptime without asserting its launch source. BootReceiver does not launch camera Activities. The reported unsolicited head-unit camera startup remains unresolved pending a diagnostic export; no camera takeover/blocking is added.

## 0.1.38

- Keep a minimum of five shortcut cells. Preserve empty positions while the initial five-cell list is not full. Once expanded beyond five, removal compacts assigned apps in order and keeps an empty add cell at the end, shrinking back to five cells when appropriate. Normalize holes in legacy expanded layouts and remove obsolete stored keys on edit to prevent shortcuts returning after restart. Preserve swapping, moving to the end, duplicate selection, shared Home/Apps state and scrolling without scrollbars.
- Version 0.1.37 was an intermediate build superseded by the clarified five-cell minimum before installation.

## 0.1.36

- Show the same right-hand controls and persistent expandable shortcuts beside the Apps grid, including its selection mode. Keep grid and shortcuts independently scrollable and reuse ZLink routing.
- Derive automatic Dock visibility from visible Home/Apps sidebar hosts rather than specific camera/ZLink launches. Show Dock after the final sidebar is hidden, including settings and other external apps; suppress it while a sidebar remains visible behind freeform ZLink. Debounce transitions between own screens and handle screen-on/unlock while the process is alive, retaining permission/recovery, screen-off and rollback gates. Hide manual sessions on sidebar screens too.
- Validate 184 JVM tests, APK signature and emulator Home/Apps/settings/external-app/fullscreen transitions. A complete Android reboot preserved every persisted shortcut assignment and slot count; recheck Dock after sleep/wake.

## 0.1.35

- Grow pinned apps beyond five cells: when every existing cell is filled, expose one additional empty cell. Preserve original slot keys and migrate five filled legacy cells without losing assignments. Reuse holes, retain positions after deletion, and persist the slot count and assignments across reboot/rollback.
- Keep the four primary rail controls stationary and scroll only the shortcut container without scrollbars or overscroll glow. Edge-hover during drag scrolls the shortcuts so off-screen positions can be reached.
- Record retrospective checkpoints for 0.1.31, 0.1.33 and 0.1.34 as local commits; 0.1.31 consolidates earlier unsnapshotted Camry changes. New successful APK builds use a local commit per validated version, with no automatic push.

## 0.1.34

- Make the shortcut removal target transparent, retain its square accent outline and replace the cross with a theme-coloured trash icon. Hover thickens the outline without adding a fill. Keep the icon and label centred as one group.

## 0.1.33

- Simplify the shortcut removal target to a compact square-cornered outline with the theme accent, a cross and Remove label. Use a dark theme fill with a turquoise hover state instead of red. Drag/drop behavior is unchanged. Version 0.1.32 was an intermediate build before the square-corner correction.

## 0.1.31

- Add five persistent icon-only shortcuts below the window-mode Home rail, separated by a divider and extra spacing. Empty cells open the existing app grid in selection mode without launching the chosen app. Selecting an existing shortcut moves it to the chosen cell.
- Hold a shortcut to drag it between cells (swapping occupied cells), or drop it on a temporary top-centre trash target to remove only the shortcut. Dropping elsewhere preserves assignments. The trash uses the existing overlay permission to remain above ZLink and closes after the drag or Home pause. Missing apps retain removable shortcuts; MODE assignments and rollback remain independent.
- Verify 178 JVM tests, APK signature and emulator selection, reordering, removal, cancellation and normal shortcut launch. Update the separate emulator-only camera fixture with a schematic surround/parking scene; no demo code is included in the product APK.
- Versions 0.1.29 and 0.1.30 were intermediate emulator builds; 0.1.31 includes the final cross-window drag cleanup.

## 0.1.28

- Restore the bounded ZLink window when Home regains focus after Back, as well as on native Home entry. Keep restoration attempts bounded and preserve local five-Back rollback. Scope lifecycle cancellation so an older settings screen cannot cancel the replacement Home request.
- Add default-off ZLink-only-in-Home routing for CarMode Apps, MODE and Dock, preserving the requested ZLink Activity. Enabling it enables Home/window mode; disabling Home/window or rolling back clears it. No ordinary-launch fallback is used after a Home-only window launch failure. External launchers and firmware fullscreen controls remain outside this routing.
- Validate 176 JVM tests, APK packaging/signature, emulator Home/Back restoration, Apps routing and the shared MODE/Dock dispatcher.

## 0.1.27

- Remove expanded left/right vertical Dock layouts. Expanded Dock is always horizontal at the bottom, retaining its raised bottom inset. Dock position now selects one of four corners for the collapsed Open Dock button. Old bottom/left/right values fall back to bottom right; corner changes update an active session immediately.

## 0.1.26

- Scope delayed ZLink launch cancellation to its owning Home controller during lifecycle teardown. Closing the Apps task no longer cancels the replacement window request on Home.

## 0.1.25

- Restore automatic Dock after leaving the Apps task for a fullscreen destination, and preserve ZLink tracking while Apps covers Home.

## 0.1.24

- Open Apps in a separate private, opaque application task with an explicit fullscreen launch hint. Sidebar, Dock and Camry ALL_APPS entries use the same tile grid above ZLink instead of changing the HOME window underneath it. Back closes the grid; its Home button returns to Camry.

## 0.1.23

- Automatically fill window-mode Camry Home with ZLink on explicit Home entry and return from its camera/settings actions. Keep one request per visit, stable-bounds/focus checks, cancellation and rollback. Separate this from the ordinary once-per-boot startup option so the boot claim cannot leave Home empty on later visits.
- Restore ZLink to the Home window after returning from its fullscreen task. Wait for focused Home/stable bounds, consume a single return attempt, and rearm only when Home remains visible beside the restored window. Preserve exit from bounded ZLink and prevent fullscreen relaunch loops when firmware ignores bounds.
- Replace Camry's app list with an adaptive, scrollable grid of square tiles, each showing the installed Activity icon above its two-line name. Load icons off the UI thread and reuse tile views; keep existing launch and recovery routes.
- Raise the bottom Dock and collapsed Open Dock control by 24 dp, leaving a 32 dp bottom inset to avoid the head unit's physical bezel.
- Reduce the window-mode sidebar to a fixed 64 dp rail with icon-only camera, apps, CarMode settings and Android settings actions. Wait for stable window coordinates before launch, avoid rebuilding Home between measuring and launching, and remove adjacent-window placement. Show automatic Dock sessions for Camry camera launches and ZLink covering Home; keep permission/recovery gates, notification close, non-sticky lifetime and full rollback.
- Expand the experimental ZLink area to the full usable Home height; remove its outline, title/date and two bottom launch buttons. Use square Camry/MODE panel corners and explicitly place Camry Home beneath ZLink launches for Back navigation instead of leaving an older OEM task underneath.
- Add default-off experimental ZLink window launching from Camry Home, using measured bounds and an Android 10 freeform mode hint, with ordinary launch and immediate disable/rollback controls. Actual head-unit compatibility remains unverified.
- Add default-off Camry Home within the existing HOME proxy, reusing the dark blue/turquoise UI. Include Android Auto, Camera, installed apps, settings, date and time; keep system navigation available.
- Add an independent non-sticky manual Dock session with Camera/Music/Home/Apps actions, OEM rollback, position, opacity and a notification close action. Collapse anchors Open Dock at the bottom right; expand restores the configured panel position. Close it on CarMode camera launches, screen-off, configuration changes or lost overlay permission. OEM reverse-camera exclusion still needs head-unit verification; no persistent or automatic Dock is enabled.
- Add optional foreground-only ZLink startup with a durable Android-boot claim, bounded attempts and cancellation on navigation/settings changes. Document unknown-boot and physical ACC-cycle limitations.
- Add durable rollback through five distinct Back presses within five seconds and explicit buttons. Preserve MODE settings, keep a recovery path, and expose Android stock-Home selection even after storage failures. Retain OEM ALL_APPS/freeform forwarding as the default path.
- Add English/Russian settings and regression coverage for rollback persistence, boot-attempt deduplication, Back timing, component boundaries and system navigation restrictions. Device acceptance remains separate from JVM/emulator checks.

## 0.1.22

- Remove the obsolete successful-ZLink-launch explanation and its resources.
- Preserve launch logging, errors, Music HTTP and Home/runtime behaviour.

## 0.1.21

- Move ZLink/factory APK copy/share tools to Archive.
- Replace persistent explanatory text with accessible inline help buttons, non-blocking Toasts and long-press tooltips.
- Keep visible status/errors/logs and important confirmations.

## 0.1.20

- Remove the mandatory return wait when a fresh OEM freeform hint is already available; accelerate a pending foreground return when it arrives late.
- Avoid synchronous boot-history commits and full Home snapshots on warm handoffs.
- Keep fallback timing, retained Home and loop recovery. User confirmed working and acceptable navigation.

## 0.1.19

- Retain the Home proxy task rather than finishing after OEM handoff; add TASK_ON_HOME.
- Use a fresh OEM freeform-move hint to restore the stock app grid on foreground return.
- Add bounce-loop recovery and an opaque Home background. User confirmed the grid worked, with a visible stutter.

## 0.1.18

- Preserve ALL_APPS/legacy allapps requests separately from normal HOME; remove RESET_TASK_IF_NEEDED.
- Record navigation intents and task/lifecycle details.
- Device test still reproduced the bug: MAIN/HOME was followed by existing settings-task resumption. Superseded by 0.1.19.

## 0.1.17

- Add default-off Music HTTP(S) GET with an editable address and 1.2-second post-launch delay.
- Use bounded timeouts, no redirects/retries, cancellation of unsent work and host-only result logging.
- Phone receiver still required; no claim of internal Android Auto selection or confirmed playback.

## 0.1.16

- Simplify app assignments to Camera/Music/Maps app pickers; preserve saved components and picker reset-to-default.
- Experiment with transparent/no-animation Home forwarding and reuse a live runtime. Transparency was superseded by retained opaque Home in 0.1.19.
- Music HTTP was not yet implemented.

## 0.1.15

- Add optional user-approved Android Home proxy, explicit original-launcher handoff, recovery and rollback.
- User confirmed startup worked on the head unit.

## 0.1.14

- Add Direct Boot, device-protected storage migration and persistent boot_history.
- Avoid automatic permissions/UI startup. Device evidence did not establish reliable autostart; later Home proxy supplied the working path.

## 0.1.13

- Add installed-app APK copy/share, including factory/system packages without launcher Activities.
- Confirm actual target platform as Android 10/API 29.

## 0.1.12

- Add manifest MODE cold-wake fallback with preserved first press, bounded wait and duplicate protection.
- Add explicit launcher Activity label/icon; this does not add factory whitelist entries.

## 0.1.11

- Separate Current operational controls from Archive legacy tests and detailed diagnostics.

## 0.1.10

- Add ZLink APK copy to Downloads and sharing; support split APK ZIPs without modifying the installed app.

## 0.1.9

- Remove tablet MacroDroid modes, targeted automation broadcasts and related controls.
- Launch assigned apps directly, using macro-derived camera/ZLink defaults and preserving explicit assignments.
- Exclude stock-app detours, Back sequences, volume-zero and disabled experiments.

## 0.1.8

- Move HCN input/overlay to a separate foreground runtime independent of Accessibility/UI lifetime.
- Add lifecycle, permission-versus-connection and Recents-removal evidence.

## 0.1.7

- Fix packaged IDE compiler stubs with isolated resource/javac compilation and artifact bytecode guards.

## 0.1.6

- Add persistent fatal Java crash reports and recovery sharing/copying.

## 0.1.5

- Implement HCN Plan A: custom menu through ordered OEM MODE broadcast interception.
- Keep modified AutoSource Plan B deferred.

## Early MVP investigation (0.1.0–0.1.4)

- Establish the Maven/Java APK, three-card menu, two-second selection, settings and optional learned-KeyEvent path.
- Expand OEM-window/input diagnostics, marked scenarios, export completeness and APK-analysis tooling.
- Keep unsupported internal-ZLink/navigation integration explicitly separate from shipped behaviour.
