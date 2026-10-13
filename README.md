# StickLed

Android app for the AYN Odin 2 family: the joystick LEDs follow the colours of the screen (left half of the screen -> left stick, right half -> right stick).

## Download
[StickLed.apk](https://github.com/merlone28/StickLed/releases/latest/download/StickLed.apk) (all versions: [Releases](https://github.com/merlone28/StickLed/releases))

1. Download the APK on the console and install it (allow installs from unknown sources if asked).
2. Open StickLed, press **Start** and accept the screen recording prompt.
3. Start a game: the LEDs follow the screen. If left and right are swapped, turn on "Inverti sinistra/destra".

## How it works
- Captures the screen at 96 px wide with MediaProjection and averages the left and right halves.
- Writes `1-R:G:B` to `/sys/class/sn3112l/led/brightness` (left) and `/sys/class/sn3112r/led/brightness` (right), the same files the Odin settings app uses. Brightness follows the level set in the Odin settings.
- On stop, the colours saved in the Odin settings are restored.

## Compatibility
Tested only on an **AYN Odin 2 Portal** (Android 13).

StickLed writes straight to the LED driver files `/sys/class/sn3112l/led/` (left stick) and `/sys/class/sn3112r/led/` (right stick). According to the source of [Bifrost](https://github.com/Pollux-MoonBench/Bifrost), another Android LED app, these same files are used by other handhelds too:

| Console | LED driver on Android | Status in StickLed |
|---|---|---|
| AYN Odin 2 Portal | `sn3112l` + `sn3112r` | Tested, works (direct write and system-service fallback) |
| AYN Odin 2 Portal Pro | same | Untested |
| AYN Thor | same (two zones per stick) | Untested, both zones are driven |
| Retroid Pocket 5 | same | Untested |
| Retroid Pocket Mini V2 | same (left stick only) | Untested, only the left stick has LEDs |

On the Portal the files can be written by any app. On other consoles they may only be writable through a system service, so StickLed first tries to write the files directly and, if that fails, sends the same command to the firmware's `PServerBinder` service (the one Bifrost uses). Both paths were tested on the Portal; on the other consoles neither has been tested. If neither path is available, StickLed shows "Impossibile controllare i LED su questo dispositivo" and stops; nothing is changed. Consoles without these files (other brands) are not supported.

Own a different console? Open an issue with the output of:
```
adb shell ls -R /sys/class/leds
adb shell settings list system
```
and support can be added.

## Build
```
./gradlew assembleDebug
```
Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir`).
