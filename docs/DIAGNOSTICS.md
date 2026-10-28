# Diagnostics, recovery and APK export

This guide describes the diagnostic UI retained in 0.1.23. Recording controls are available in Current/Archive; guided tests, detailed selectable logs, optional Accessibility tools and APK exports are in **Archive**. Optional [Camry Home and Dock](CAMRY-HOME.md) add Current-tab controls without moving these tools. All tests must be performed while parked.

## Recording a live MODE or navigation problem

1. Finish or cancel any guided test and key-learning session. Guided tests are passive; ordinary recording does not disable live HCN interception.
2. Check Overlay permission, MODE/HCN enablement and the foreground-runtime status. Accessibility is not required for HCN events, but is needed for window/KeyEvent investigation.
3. Stop recording and clear logs if a clean run is wanted. Clearing requires confirmation.
4. Start ordinary recording, reproduce a precisely described sequence and note which apps/menu actually appeared.
5. Stop and export JSON through Android sharing. Include the installed version, button/gesture sequence and whether this was cold boot or sleep/wake.

For MODE, test separate sequences of one/two/three/four clicks, waiting two seconds after the last press. Check holding/release and the first-card reset. For Home/grid failures, record Home, the swipe/grid action, opening another app and Back separately. A registered receiver is not proof of signal delivery; a successful launch request is not proof of foreground display.

## Guided scenarios

Use Archive for causal investigation of input, not for judging the replacement menu. HCN and learned input are allowed through to the stock handler during these scenarios.

- **No presses:** wait for the entire run without touching steering buttons or the screen. This is the baseline.
- **MODE:** after the five-second countdown, press physical MODE once at each of the three prompts, two seconds apart. Wait through the five-second observation period after the third prompt. Total duration is 16 seconds.
- **Other steering button:** follow the same prompts using one named non-MODE button. Report which button was used; do not mix several buttons in one run.
- **USB/Bluetooth keyboard:** connect a normal keyboard and enable the optional Accessibility service. Follow the key-test prompts with the requested standard input to check whether onKeyEvent is delivered at all. Do not assign an arbitrary keyboard Enter to steering MODE merely because an OEM broadcast embeds code 66.

Run the baseline first, MODE next, then one other button; export once after the sequence. The logger cannot record input Android never delivers. A zero KeyEvent count does not mean there were no HCN broadcasts.

## What is recorded

- HCN: recognized action/flags, ordered/abortable status, allowlisted nested KeyEvent fields, auxiliary KEYCODE, handling/overlay state, menu advancement, abort result and reason. Unknown extras are not dumped. Events can be persisted outside ordinary recording.
- Accessibility KeyEvent: keyCode, scanCode, deviceId, source, action, repeatCount, eventTime and consumed status, even before a MODE key is learned. Call counters distinguish no callback from unmatched input.
- Accessibility state: exact secure-setting membership versus actual connection; master state, component state, current/previous service callback, PID and runtime/background evidence. Enabled competing key filters produce a warning, not proof of a conflict.
- OEM windows: windowId, type, layer, bounds, focused/active and root availability; OEM title and bounded element counts where accessible. Inspect at event receipt, with delayed follow-up snapshots and bounded polling. Unknown ownership or missing roots remain explicit.
- OEM nodes: bounded structural/selection/focus metadata for com.hcn.autosource, not arbitrary text from other applications.
- Window transitions: previous/current package/class, monotonic interval, scenario ID and window snapshot. current_activity is populated only when PackageManager resolves the event class as an Activity; RelativeLayout is not an Activity.
- Home/runtime: recognized incoming/outgoing navigation fields, task identity/lifecycle, retained-Home/freeform-return stages, launch requests, errors and boot evidence.

CLOSE_SYSTEM_DIALOGS reasons are logged separately and are not interpreted as MODE. Window changes and nearby timestamps establish observations, not who initiated a launch. Public broadcasts do not authenticate the physical sender. stock_suppression_confirmed and foreground_confirmed remain false unless separately observed; invoking an API is insufficient proof.

Current HCN receivers cover KSRC_BUTTON and com.hcn.autokey, not every OEM command. See [key catalogue](HCN-KEY-CATALOG.md) for unobserved routes and unsafe mapping-listener APIs.

## Export completeness

Schema version 2 / oem_source_v2 includes sessions with UUIDs, start/end/state and key counts, plus scenario/phase boundaries. Up to 32 session summaries and 64 scenario outcomes per session are retained independently of event rotation.

New events carry recording_active, session_id, scenario_id and scenario_phase. Events outside recording have session_id=null. An interrupted session/test after process death has an unknown exact end time, not a fabricated timestamp. Root settings are captured **at export**, so diagnostics=false after stopping does not contradict an earlier diagnostics_started event.

Two rotated JSONL files bound event storage to approximately 512 KiB combined; export includes both. The visible log shows the latest 30 in-process entries, not the whole persisted history. Completeness reports rotation loss, write/permission errors, malformed lines and discarded summaries. lost_events is an estimate of detected storage loss, not a count of events Android withheld; partial writes can contribute to overlapping counters.

Metadata checkpoints are asynchronous, roughly once per second and at boundaries; abrupt termination can lose recent metadata. Persistent boot_history holds the latest 32 stages and survives normal log clearing. Service evidence and crash reports are separate from the rotated event files.

## Copying, scrolling and clearing

Long-press the visible log to select/copy text, or use its clipboard button to copy all displayed text. Export JSON for the complete retained log. UI text changes only when content changes; refresh is deferred during touch, selection and scrolling/inertia. Event collection continues, and page position is retained after refresh.

Clear logs cancels an active scenario and removes internal event files, visible history, session summaries and the temporary JSON export. It preserves settings, boot history, separate crash reports, cached APK copies and files already shared elsewhere. If recording remains enabled, a new session starts; process-wide KeyEvent counts are not reset. Stop first for a clean idle baseline.

## Crash recovery

1. Install updates over the existing app without clearing data.
2. After a fatal Java crash, reopen the app. Recovery should open instead of ordinary settings, without requiring diagnostics to have been enabled.
3. Share carmode-crash.txt, copy the report or select its text. Review it before sharing; exception messages and paths may contain private details.
4. Opening settings without MODE handling explicitly disables general handling, HCN and recording, clears the recovery gate and attempts settings startup. Assignments and previous logs remain. Re-enable desired switches after the cause is resolved.

The handler is installed before normal logging. It saves application/Android version, thread, exception, stack trace and cause chain, then delegates to Android termination; it does not resume the failed thread or send data automatically. An unacknowledged original report is not replaced by a recovery-screen failure. After acknowledging recovery, a later crash can replace it. Reports are bounded to 64 Ki characters plus a truncation marker, shared through temporary read-only URI grants, and included as last_crash_report in ordinary JSON exports. Clear logs does not remove them.

Force-stop, ANRs, native crashes, power loss, full storage and severe memory exhaustion may not produce this Java report.

### Historical packaging failure

The 0.1.6 report showed java.lang.Error: Unresolved compilation problems: I18n cannot be resolved from EventLog.recent() during settings creation. The compiler stub was confirmed in the local JAR and APK DEX. This was a packaging defect, not evidence of CANBUS/phone incompatibility; the exact IDE process overwriting shared classes was not established.

Version 0.1.7 introduced isolated javac packaging and checks of the final artifacts. An old retained 0.1.6 report after updating is not itself a fresh crash. Open settings with handling disabled, then re-enable settings once startup succeeds. A phone can validate UI startup, but not the head unit's HCN delivery or OEM suppression.

## Exporting installed APKs

Archive provides AutoSource export, ZLink copy/share and an installed-app picker for factory settings or another app, including packages without a launcher Activity. Candidate ordering does not prove which package implements factory settings.

Copy only installed base/split APK paths obtained from ApplicationInfo. A single APK is unchanged; multiple splits form a ZIP. Validate the package/version/file set around copying and retry if it changed. No app-private data, installation, package disabling, repacking or re-signing occurs. No root, ADB, Accessibility, Overlay or external-storage permission is required for these exports; unreadable files produce an error, not a bypass.

Sharing uses an individually addressed completed cache file and a temporary read-only content URI grant. Incomplete files are unavailable. Download copies use Android 10 MediaStore without overwriting prior exports. Cancelling the chooser sends nothing; opening it does not prove recipient delivery. APK cache files remain until normal cache management or Android cache clearing removes them.

Build/JVM checks do not replace Android sharing, recovery, input, scrolling or firmware-lifetime tests on the device.
