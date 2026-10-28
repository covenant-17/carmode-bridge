# HCN MODE: implementation A and deferred plan B

Current status (0.1.23): Plan A is the working device-reported MODE implementation; startup and stock-grid handoff use the user-approved [Home proxy](HOME-PROXY.md). Optional [Camry Home](CAMRY-HOME.md) leaves this MODE route intact. The checklist below remains useful for regressions, but its original “unverified” wording describes the implementation-stage test boundary. Plan B has not been implemented and is not a fallback selected automatically.

## Shared behavior

Three cards: 360 Camera, ZLink Music, ZLink Google Maps. Every opening starts at camera; subsequent MODE clicks cycle and reset a two-second graphical countdown. Timeout executes one selection. No card touch navigation, saved selection or reverse-gear exclusion. Music and Maps currently open ZLink, not a specific Android Auto screen. Maven only.

## A — Bridge menu (implemented in 0.1.5; foreground lifetime added in 0.1.8)

- Opt-in `hcn_mode` preference defaults to false, preserving existing settings on upgrade. While enabled, HCN replaces learned KeyEvent routing. The global handling switch disables either route.
- A context receiver lives exclusively in ModeRuntimeService (foreground, stopWithTask=false), priority 999. HCN needs Overlay but no Accessibility permission. It handles `android.intent.action.KSRC_BUTTON`; `com.hcn.autokey` is observation-only, including KEYCODE 61. No OEM package/service modification.
- Only abortable ordered delivery can open/advance our menu. Abort follows successful menu display; display/abort errors preserve native delivery and cancel partial selections. Missing permissions, disabled handling, guided scenarios and learning pass through.
- Missing KEY_EVENT extra is valid (the original receiver ignores it). Malformed payloads fail open. Valid fresh DOWN is accepted regardless of the embedded keyCode. UP/repeats do not advance; follow-ups matching an accepted DOWN are suppressed to prevent AutoSource treating them as fresh clicks. No timing debounce that would discard rapid genuine presses.
- One selected input source prevents KeyEvent/KSRC duplication; AUX is never a second input. Public broadcasts cannot establish physical sender authenticity. Only fixed configured menu actions run; no arbitrary incoming component or raw extras forwarding.
- Receiver status, last channel/order/abort/reason, event whitelisting and session/scenario annotations provide evidence without claiming the stock menu was actually hidden. Passive guided tests are explicitly distinguished from ordinary recording and live replacement checks.
- BOOT_COMPLETED starts the foreground runtime when auto-start and handling are enabled; the system still owns Accessibility binding for the legacy key path. Keep the original debug signing key and user settings. No root/ADB/firmware modification. See BACKGROUND-LIFECYCLE.md for OEM limitations and acceptance checks.

### Verification

Run `mvn -B verify` using JDK 17. Unit coverage includes disabled/not-ready/passive routes, malformed payloads, absent payload, ordinary/no-abort delivery, missing overlay, successful display-before-abort order, display/abort exceptions, held events, exclusive input, fast clicks and menu timeout/reset behavior. Build validates signature and alignment.

On the head unit: validate one/two/three/four clicks, hold/release, only one visible menu, reset to first card, assigned app launch, other steering buttons, cold boot/sleep, disabling Accessibility/handling, overlay revocation, and exported HCN logs. Zero Accessibility KeyEvents does not invalidate HCN delivery. Device behavior remains unverified until this run.

## B — modified AutoSource with Bridge actions (not implemented)

This is a full behavioral equivalent, not a resource-only skin. Do not start B automatically if A fails.

1. Preserve the local input `docs/local/com.hcn.autosource.apk` unchanged (SHA-256 `D3F56574D09A8DF8222C313C840DFB6B423B7ED09F0AF52223B13D99A988C556`). This OEM APK is intentionally not included in Git. Decode a working copy and rebuild under a separate Maven profile. Patch resources and targeted smali; do not rebuild all decompiled Java with missing OEM dependencies.
2. Replace the stock GalleryFlow with three centered cards, selected icon treatment and top countdown bar. Update applicable resource qualifiers, labels and spoken labels consistently. Replace the six-entry source/action mapping and alternate theme mapping together so counts and selection stay aligned.
3. Start every opening at index zero, cycle on MODE, reset timeout to 2000 ms, execute once, then close. Remove current-source initialization and the reverse-gear guard from this selection path. Cancel pending callbacks on close/service destruction.
4. Keep launch assignments and Direct/MacroDroid behavior in Bridge. Add a separately versioned explicit, exported no-UI Bridge Activity for selection, accepting only the three fixed action identifiers; reject unknown values and arbitrary supplied components, dispatch using existing settings, then finish. An explicit external call is not authenticated as a steering press. If Bridge is missing/unavailable, show a clear failure and do not launch an old stock mapping.
5. Disable HCN interception in Bridge for B: AutoSource owns input and menu, Bridge only executes the selected action. No duplicate timers or automatic A/B switching.
6. Verify modified package identity, resources, DEX, version increment, alignment and signature. The exported certificate matches the AOSP platform certificate, but installation/update acceptance and firmware policy remain unverified. Do not sign with the Bridge debug key and claim it updates AutoSource. Resolve a compatible signing/install path before device rollout.
7. Output a distinct modified APK; never overwrite the exported original. Device installation, backup and restoration procedure require a separate agreed step. Do not uninstall the system app or silently use root/ADB.

### B acceptance and rollback gate

Verify exact three-card/first-card/two-second behavior and no launches of the removed Radio/Bluetooth/media mappings, EN/ru presentation, Bridge dispatch in each action mode, missing Bridge/error handling, reboot and sleep, and unchanged unrelated OEM functions. Before installation establish whether the head unit permits removing an installed update to restore the system version or reinstalling the preserved original; do not assume a lower-version APK can simply be installed over the modified one. If no safe update/restoration path is available, report the blocker without touching the installed OEM package.
