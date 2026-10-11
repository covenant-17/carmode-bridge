# Changelog

Historical implementation notes. Current operation and setup are documented in [README](README.md). Device confirmation is stated only where reported; earlier experiments are not current guarantees.

## Unreleased

- Translate remaining documentation to English and reorganize README around the current product.
- Document the full 93-command static OEM key catalogue and diagnostic/recovery procedures.
- Initialize an independent local Git repository; keep source and Markdown, move device evidence to ignored docs/local/, and ignore local scripts, signing material and generated output. Scripts must be copied separately to build a fresh clone.
- Add a source-level regression guard for English-default settings and MODE labels while preserving an explicit Russian choice.
- Documentation/test-only maintenance: application version remains 0.1.22; no new APK.

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
