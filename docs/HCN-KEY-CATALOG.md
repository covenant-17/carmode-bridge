# HCN key catalogue — static APK analysis

Analysed on 2026-10-11. This is a catalogue of OEM logical commands, not a complete CAN protocol or a verified mapping of physical steering-wheel buttons. No application behaviour, firmware settings or key maps were changed.

## Evidence and limits

Local source: `docs/local/com.hcn.autosource.apk`, SHA-256 `D3F56574D09A8DF8222C313C840DFB6B423B7ED09F0AF52223B13D99A988C556`. The original APK is retained locally and intentionally excluded from Git.

The APK contains copies of OEM framework classes. Their code establishes how this supplied implementation works; the framework actually running on the head unit could differ. Decompiled Java is evidence, not a guarantee of complete recovery of every branch. The catalogue covers all **93 `K_*` integer constants** in `android.carsource.McuConstant`.

Local evidence, relative to the repository:

- `.local/autosource-analysis/sources/android/carsource/McuConstant.java:46-138`: all constants.
- `.local/autosource-analysis/sources/android/carsource/McuService.java:257-727`: key callback and command dispatch.
- `McuService.java:1041-1120`: callback registration/removal and key-mapping side effects.
- `McuService.java:1193-1243`: Android input injection, raw callbacks, media key dispatch.
- `McuService.java:1437-1534`: MCU key packets, ADC maps and mapping mode.
- `McuService.java:1736-1769`: additional Android input aliases.
- `.local/autosource-analysis/sources/android/carsource/KeyButton.java:10-15,87-139`: internal press states and timing.
- `.local/autosource-analysis/sources/android/Configures/HConfig.java:133-134`: USER1/USER2 setting names.
- `.local/autosource-analysis/sources/com/hcn/autosource/AutoSourceService.java:55-58,141-150`: auxiliary event consumer.

`McuService.java` references below mean the file in the same decompilation directory. `.local` evidence is not a portable part of the documentation; the original APK is the reproducible input.

## Routes and replacement feasibility

| Route | What the supplied code does | Consequence for CarMode |
| --- | --- | --- |
| O — ordered broadcast | Emits `KSRC_BUTTON` for SOURCE | Existing MODE replacement uses this route; confirmed working by the user. This does not imply every key uses it. |
| B — ordinary broadcast | Emits voice, Bluetooth or global-button intents | Candidate for observation with additional receivers. No ordered-abort mechanism at these send sites; adding our action would not itself suppress the native action. |
| M — media dispatch | Calls `AudioManager.dispatchMediaKeyEvent()` | Not delivered to our current HCN receiver. Media routing and ownership must be investigated; cannot promise global capture or native suppression. |
| I — injected Android input | Calls `InputManager.injectInputEvent()` | Not an HCN broadcast. May be visible in the input recipient or optional Accessibility path, depending on system filtering. No global interception has been verified. |
| D — direct system action | Changes volume, display, power, launches an Activity, etc. | No replacement hook demonstrated in this callback. Need another OEM/configuration route, not just a broadcast receiver. |
| U — user-configured Activity | Reads `Settings.System` and launches a configured component | Promising native configuration route for USER1/USER2. Actual setting access and physical-button assignment remain unverified. |
| ? — declaration only | Constant exists but no dedicated branch was identified in the main callback | Name is not proof that a button is implemented or emitted on this head unit. |

Existing `HcnModeReceiver` registers only `android.intent.action.KSRC_BUTTON` and `com.hcn.autokey`. AUX is observation-only. `KSRC_BUTTON` is treated as MODE by its **action**, not by requiring embedded keyCode 66. The handler accepts fresh DOWN or absent payload, suppresses matching accepted UP/repeats, and requires abortable ordered delivery plus a working overlay.

## Most relevant commands

| Decimal code | OEM constant | Native behaviour in the analysed callback | Route |
| --- | --- | --- | --- |
| 285 | `K_SOURCE` | Unmutes if needed, emits ordered `android.intent.action.KSRC_BUTTON` with a newly constructed DOWN `KeyEvent`, keyCode **66**, repeat 0, meta 0; Intent flag 32 | O |
| 24 | `K_VOLUME_INC` | Raises selected audio-stream volume; repeat/long-press handling is special | D |
| 25 | `K_VOLUME_DEC` | Lowers selected audio-stream volume; repeat/long-press handling is special | D |
| 164 | `K_MUTE` | Toggles mute through `adjustVolume`; also participates in screenshot-chord state | D |
| 85 | `K_PLAY_PAUSE` | Dispatches media key 85 | M |
| 86 | `K_STOP` | Dispatches media key 86 | M |
| 87 | `K_NEXT` | Dispatches media key 87 | M |
| 88 | `K_PREV` | Dispatches media key 88 | M |
| 89 | `K_FAST_BACKWARD` | Dispatches media key 89 | M |
| 90 | `K_FAST_FORWARD` | Dispatches media key 90 | M |
| 126 | `K_PLAY` | Dispatches media key 126 | M |
| 127 | `K_PAUSE` | Dispatches media key 127 | M |
| 231 | `K_ASSISTANT` | Broadcasts `com.hcn.voicesystem.action.StartVoice` | B |
| 302 | `K_BT` | Bluetooth/call action. Default route broadcasts `com.cardroid.action.START_cardroid.bt`, embedded DOWN keyCode 5. Phone-link route uses configuration from `/system/etc/btcall.cfg` and call state | B |
| 307 | `K_HANGDOWN` | Default route broadcasts the same Bluetooth action, embedded DOWN keyCode 6. Phone-link route uses configured hangup Intent | B |
| 91 | `K_MICMUTE` | Broadcasts `android.intent.action.GLOBAL_BUTTON`, embedded keyCode 91 | B |
| 3 | `K_HOME` | Normal effective press collapses panels and injects Home. Internal long-press state 2 starts explicit SystemUI `RecentsActivity` | I / D |
| 4 | `K_BACK` | Injects Back on an effective press; participates in screenshot-chord checks | I |
| 82 | `K_MENU` | Injects Menu | I |
| 66 | `K_ENTER` | Injects Enter. **Not the same as SOURCE 285**, although SOURCE's broadcast payload uses 66 | I |
| 209 | `K_MUSIC` | Starts `com.hcn.AutoMediaPlayer/com.hcn.MediaActivity.MusicPlayerUiActivity` | D |
| 298 | `K_NAVI` | Calls `Util.launchNavigation()`; target is determined by OEM navigation logic, not hardcoded in this branch | D |
| 304 | `K_BAND` | Opens `SourceService.ACTIVE_RADIO` if not on top; otherwise dispatches media key 30 with meta 2, with launch/release guard | D / M |
| 301 | `K_AUX_IN` | Opens `com.auto.hcamera/.VideoInActivity`, or injects Back if that Activity is already on top | D / I |
| 331 | `K_BTMUSIC` | Starts `com.autochips.bluetooth/.BtMusicActivity` when its top-Activity check allows | D |
| 332 | `K_VIDEO` | Starts `com.hcn.AutoMediaPlayer/com.hcn.MediaActivity.VideoPlayerUiActivity` | D |
| 330 | `K_USER1` | Reads `Settings.System["user_key1_action"]`; if nonempty, launches the flattened component | U |
| 333 | `K_USER2` | Reads `Settings.System["user_key2_action"]`; if nonempty, launches the flattened component | U |

For Bluetooth, `isNeedSendKeyToZJ()` depends on property `zj.phonelink.type` and Bluetooth connection status. `/system/etc/btcall.cfg` was not supplied, so the actual phone-link action/package/extra combinations are **unknown**, not guessed from names.

## Other declared commands

All remaining constants are included below. For route `?`, descriptions are based on the constant name only; no dedicated action was established in the analysed main callback.

| Decimal code | OEM constant | Observed behaviour or declaration | Route |
| --- | --- | --- | --- |
| 7 | `K_0` | Injects digit 0 | I |
| 8 | `K_1` | Injects digit 1 | I |
| 9 | `K_2` | Injects digit 2 | I |
| 10 | `K_3` | Injects digit 3 | I |
| 11 | `K_4` | Injects digit 4 | I |
| 12 | `K_5` | Injects digit 5 | I |
| 13 | `K_6` | Injects digit 6 | I |
| 14 | `K_7` | Injects digit 7 | I |
| 15 | `K_8` | Injects digit 8 | I |
| 16 | `K_9` | Injects digit 9 | I |
| 17 | `K_STAR` | Injects star | I |
| 18 | `K_POUND` | Injects pound/hash | I |
| 19 | `K_UP` | Injects direction Up | I |
| 20 | `K_DOWN` | Injects direction Down | I |
| 21 | `K_LEFT` | Injects direction Left | I |
| 22 | `K_RIGHT` | Injects direction Right | I |
| 26 | `K_POWER` | Calls `SilenceMode(false, false, true)`; do not equate this with a normal app-level power key | D |
| 62 | `K_SPACE` | Injects Space | I |
| 67 | `K_CLEAR` | Injects key 67 (delete/backspace) | I |
| 129 | `K_EJECT` | Eject declaration | ? |
| 255 | `K_SWCSETTING` | Calls `onRawKeyEvent(mAdc, state)` on internal long press or short release | Raw callback |
| 272 | `K_SKIP_FORWARD` | Dispatches media key 272 | M |
| 273 | `K_SKIP_BACKWARD` | Dispatches media key 273 | M |
| 274 | `K_STEP_FORWARD` | Dispatches media key 274 | M |
| 275 | `K_STEP_BACKWARD` | Dispatches media key 275 | M |
| 284 | `K_BASE` | Base constant; not evidence of a physical button | ? |
| 286 | `K_REPEAT` | Dispatches media key 46, meta 2 | M |
| 287 | `K_DVD_AUDIO` | DVD audio declaration | ? |
| 288 | `K_DVD_ANGLE` | DVD angle declaration | ? |
| 289 | `K_DVD_TITLE` | DVD title declaration | ? |
| 290 | `K_DVD_S_TITLE` | DVD subtitle declaration | ? |
| 291 | `K_DVD_MENU` | DVD menu declaration | ? |
| 292 | `K_DVD_SLOW` | DVD slow-play declaration | ? |
| 293 | `K_DVD_ZOOM` | DVD zoom declaration | ? |
| 294 | `K_RDSTA` | Calls `RadioService.setTA()` if the service exists | D |
| 295 | `K_SCAN` | Dispatches media key 31, meta 2 | M |
| 296 | `K_RANDOM` | Dispatches media key 47, meta 2 | M |
| 297 | `K_AS` | Dispatches media key 29, meta 2; OEM symbolic label AS/PS | M |
| 299 | `K_TV` | TV declaration | ? |
| 300 | `K_DVD` | DVD declaration | ? |
| 303 | `K_IPOD` | iPod declaration | ? |
| 306 | `K_ANDROID` | Android declaration | ? |
| 308 | `K_EQ` | Starts equalizer, or injects key 142 when its Activity/package is already on top | D / I |
| 309 | `K_BKLIGHT_OFF` | Sets backlight state false and calls `backlightOnoff()` | D |
| 310 | `K_BACKLIGHT` | Toggles backlight state and calls `backlightOnoff()` | D |
| 311 | `K_BKLIGHT_ON` | Sets backlight state true and calls `backlightOnoff()` | D |
| 312 | `K_SCROLL_R` | Dispatches media key 46, meta 1 | M |
| 313 | `K_SCROLL_L` | Dispatches media key 40, meta 1 | M |
| 314 | `K_SETUP` | Starts `android.settings.SETTINGS` | D |
| 315 | `K_REBOOT` | Calls `PowerManager.reboot(null)`; **do not synthesize/test casually** | D |
| 316 | `K_PS0` | Dispatches media key 7, meta 2 | M |
| 317 | `K_PS1` | Dispatches media key 8, meta 2 | M |
| 318 | `K_PS2` | Dispatches media key 9, meta 2 | M |
| 319 | `K_PS3` | Dispatches media key 10, meta 2 | M |
| 320 | `K_PS4` | Dispatches media key 11, meta 2 | M |
| 321 | `K_PS5` | Dispatches media key 12, meta 2 | M |
| 322 | `K_PS6` | Dispatches media key 13, meta 2 | M |
| 323 | `K_PS7` | Dispatches media key 14, meta 2 | M |
| 324 | `K_PS8` | Dispatches media key 15, meta 2 | M |
| 325 | `K_PS9` | Dispatches media key 16, meta 2 | M |
| 326 | `K_LOUD` | Loudness declaration | ? |
| 327 | `K_STEREO` | Dispatches media key 43, meta 1 | M |
| 328 | `K_PBC` | PBC declaration | ? |
| 329 | `K_TIMESETTTING` | Starts `android.settings.DATE_SETTINGS`; spelling retained from OEM | D |
| 334 | `K_MOTOR` | Calls `toggleMotor()`; physical-screen motor function, not a steering event hook | D |

The OEM symbolic-name initializer reverses the ON/OFF labels for 309 and 311. The catalogue follows their actual callback branches and constant definitions, not those misleading log labels.

## Known event combinations outside the constants table

| Input or event | Payload / conversion | Evidence and current coverage |
| --- | --- | --- |
| `android.intent.action.KSRC_BUTTON` | SOURCE 285 becomes embedded KeyEvent DOWN **66** | `McuService.java:687-690`; CarMode handles it |
| `com.hcn.autokey` | Integer extra `KEYCODE`; AutoSource reacts to **61** by creating its floating window | AutoSource consumer only; producer and full code set not established here. CarMode logs this channel, does not handle it |
| `com.hcn.voicesystem.action.StartVoice` | No extras at this send site | Assistant 231; CarMode currently has no receiver |
| `com.cardroid.action.START_cardroid.bt` | Embedded DOWN KeyEvent **5** for BT, **6** for hangup | Default phone route; CarMode currently has no receiver |
| `android.intent.action.GLOBAL_BUTTON` | Embedded keyCode **91**, Android action selected from internal state | MICMUTE; CarMode currently has no receiver |
| Android input keyCode **140** | Resolves a touch-key mapping for SOURCE 285 | `interceptFilterEvent()` alias; not a second physical MODE identity |
| Android input keyCode **172** | Injects OEM NAVI 298 | Same method |
| Android input keyCode **200** | Injects OEM VIDEO 332 with a 200 ms timeout | Same method |
| Android input keyCode **195** | Injects OEM BT 302 with a 200 ms timeout | Same method |
| Android input keyCode **196** | Injects OEM HANGDOWN 307 with a 200 ms timeout | Same method |

Internal `KeyButton` states are **not Android KeyEvent actions**:

| Internal value | Meaning |
| --- | --- |
| 1 | Pressed |
| 2 | Long pressed |
| 3 | Second long-press threshold |
| 4 | Repeating |
| 5 | Released |
| 6 | Cancelled |

`KeyButtonATTR` has separate normal, long and second-long command codes plus repeat and timeout settings. Thus a held physical button can resolve to a different logical command, depending on installed maps. `BoardCastMediaKey()` converts internal 1 to Android DOWN, 5 to UP, and 2 to DOWN with flag 128; meta values listed above are the actual constructor metaState, not CAN flags.

## MCU mapping and an unsafe-looking diagnostic shortcut

The analysed service parses MCU group `GID_KEY = 6`, subcommand 1: source byte, 16-bit ADC/key value and state byte. Release state 0 releases pressed keys. Other states are resolved through panel, SWC or remote maps. This is an **MCU-to-Android protocol**, not the underlying Toyota CAN frame format. The installed ADC-to-command assignments are not present in the APK.

`McuManager` looks up Binder service `mcu_service` and exposes `getKeySettings()`, `setOnEventListener()` and mapping APIs. These are vendor APIs, not verified usable APIs for our ordinary package.

**Do not add a blanket MCU key listener as if it were read-only logging.** `setcallback()` with SWC/PANEL/TOUCH/FACTORY masks switches global mapping/test flags; SWC/PANEL/TOUCH registration also clears staging maps. `onAdcKey()` then takes a mapping path. Removing callbacks can persist maps, unless the cancellation bit `CNACEL_KEY = 268435456` is supplied. This can change or interrupt native button behaviour. No such calls were made during this investigation.

The callback's other gates include ACC/upgrade state, call state, reverse state, silence/backlight state, RDS and screenshot chords. An OEM command can therefore exist but produce no event in a particular vehicle state. Physical assignment, context, short/long press and destination route must all be recorded before adding replacement rules.

## Recommended next investigation, not implemented

1. Extend opt-in observation to the explicitly identified ordinary voice/BT/global-button actions, recording only known scalar/KeyEvent fields; no broad extra dumps, event injection or abort.
2. Test actual steering buttons one at a time with short and long presses; correlate each with a marked scenario. Current logs only cover KSRC/AUX broadcasts and the existing optional input diagnostics, not all routes in this catalogue.
3. Investigate whether firmware offers assignment to USER1/USER2 and read their configured targets without changing them. These are promising native Activity slots, not permission or API guarantees.
4. Investigate media/input routes separately if track buttons are desired. Do not claim that receiving a second event or observing an app transition cancels the native action.
5. Keep working MODE/Home handling unchanged until another route is confirmed. Do not write OEM key maps, register mapping callbacks, synthesize reboot/power commands or replace system APKs for discovery.

No APK was rebuilt: this change is documentation only, so application version remains 0.1.22.
