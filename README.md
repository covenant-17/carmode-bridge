# CarMode Bridge

A native Android app that replaces the stock MODE menu on aftermarket head units with HCN OEM software, tested on **UIS8581A running Android 10 / API 29**. It receives steering-wheel MODE presses through HCN OEM broadcasts rather than relying on standard Android key input. This input path works **without Accessibility access or root**, and leaves system APKs unchanged.

Current application version: **0.1.22**, Android versionCode **23**.

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

Music and Maps currently **open their assigned app**. They do not automatically select a Music/Google Maps screen inside Android Auto.

## How MODE and startup work

The working input path is steering wheel → CANBUS/MCU → OEM `android.intent.action.KSRC_BUTTON` broadcast → CarMode's foreground handler. CarMode displays its menu and interrupts further **abortable ordered** delivery only after the menu opens successfully. The stock AutoSource APK is not patched or disabled.

HCN handling does **not** require Accessibility or MacroDroid. An optional Accessibility service remains for learned Android KeyEvents and OEM-window diagnostics; it is not the primary input path on this head unit.

Ordinary boot/Direct Boot receivers did not establish reliable startup on the tested firmware. The working workaround is an optional **Home proxy**:

1. The user selects CarMode as Android's default Home app.
2. Android opens the proxy, which brings up the MODE runtime when enabled.
3. The proxy hands the screen to the original `com.android.launcher3/com.android.launcher3.Launcher`.
4. A retained Home task and the OEM freeform signal restore the stock installed-app grid instead of resuming the previous app.

The user confirmed startup through Home in 0.1.15, the stock app grid in 0.1.19, and acceptable navigation smoothness in 0.1.20. This is firmware-specific evidence, not a guarantee for other devices or every OEM gesture.

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

Clone the repository, enter its directory and run the Maven build. All required project build scripts are included:

```powershell
git clone https://github.com/covenant-17/carmode-bridge.git
cd carmode-bridge
```

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
mvn -B clean verify
```

The SDK defaults to `%LOCALAPPDATA%/Android/Sdk`. Override it if necessary:

```powershell
mvn -B clean verify '-Dandroid.sdk=D:/Android/Sdk'
```

Output: `target/carmode-bridge-0.1.22-debug.apk`.

- `mvn test`: Java unit tests and compiler-stub guard regressions, without APK packaging.
- `mvn package`: tests and APK compilation, alignment and signature validation.
- `mvn verify`: the complete lifecycle through packaging and verification.

Maven invokes AAPT2, javac, D8, zipalign and apksigner through the included PowerShell scripts in `scripts/`; no separate script download or manual invocation is needed. APK packaging uses a fresh private `target/apk-staging/<uuid>` directory rather than trusting IDE-writable `target/classes`. JAR, DEX and signed APK checks reject `Unresolved compilation problem` compiler stubs. The APK is attached to the Maven artifact with classifier `debug`.

Open `pom.xml` as a Maven project in your IDE. Android Studio Gradle Run/Sync is not supported. Use JDK 17: the configured Build Tools/D8 combination previously failed with JDK 21.

Every changed application release must increment both the patch version and versionCode: next **0.1.23 / 24**. Keep `pom.xml` and `src/main/AndroidManifest.xml` aligned. Documentation-only changes do not produce a new APK or increment the application version.

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
7. Choose CarMode as Home through the Current tab and approve the Android selection. Verify that Home still shows the original launcher.
8. Fully reboot, do not manually open CarMode, and check the steering-wheel MODE button. Also test Home/Back, the installed-app grid and sleep/wake separately.

The startup switch alone does not select Home or obtain an OEM whitelist entry. Android 10 has no ordinary runtime permission dialog for BOOT_COMPLETED. CarMode does not add itself to the factory **Startup Application** list, which uses fixed OEM XML entries.

For the alternative learned-key route, disable HCN, grant Accessibility manually, start learning in Archive, press MODE and confirm the observed key. Learning is passive and does not consume input.

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
