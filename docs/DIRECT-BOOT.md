# Direct Boot — 0.1.14

Historical result: the user's 0.1.14 device test did not establish autostart; no boot delivery was recorded and the runtime began after manual opening. Direct Boot/storage support remains implemented, but the user-confirmed startup workaround is the optional [Home proxy](HOME-PROXY.md). The procedure below documents that earlier experiment.

BootReceiver and ModeRuntimeService are directBootAware; LOCKED_BOOT_COMPLETED supplements normal BOOT_COMPLETED and package-update handling. A dynamically registered USER_UNLOCKED listener retries initialization when necessary. No UI, Accessibility grant, shell command or OEM setting is triggered at boot.

Settings, session metadata, lifecycle evidence, event files and crash reports use device-protected storage consistently. On the first unlocked start after update, existing preferences and known event/crash files are migrated. Destination data is never overwritten; the migration marker is committed only after success. Open once after updating, before reboot. If the first start is locked without completed migration, startup waits for unlock instead of assuming defaults. Technical diagnostics become available before user unlock; no credentials are stored here.

JSON exports include `boot_history`: 32 latest synchronously persisted stages, independent of diagnostics recording and crash recovery. Stages cover process creation, boot/unlock/update delivery, skipped startup, runtime requests/errors, foreground activation and HCN registration. Normal log clearing does not erase this history. Missing events do not prove a specific firmware restriction.

## Device test

1. Install 0.1.14 over the previous app, without uninstalling or clearing data.
2. Open once. Verify assignments/language; enable auto-start, handling, HCN and Overlay. Accessibility is optional.
3. Reboot fully. Do not open CarMode, even through Recents. Test one, two and three MODE presses, waiting two seconds for selection.
4. Then open CarMode and export JSON, even if MODE failed. Check `boot_history` for delivery, errors and receiver registration.
5. Also test swiping UI away, log/APK sharing and retained crash reports.

This is an additional Android boot path, not a bypass of firmware policy or force-stop. Target remains API 29. Factory Startup Application membership is independent: com.hcn.autosetting reads `/apd/coldstart_launch_app_list.xml`, falling back to `/system/etc/coldstart_launch_app_list.xml`. Selection uses Settings.System `coldstart_auto_launch_app`; shell changes remain unverified and did not establish autostart. Neither setting nor XML is edited by CarMode.
