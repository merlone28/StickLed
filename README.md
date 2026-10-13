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

StickLed writes straight to the Portal's LED driver files (`/sys/class/sn3112l/led/` and `/sys/class/sn3112r/led/`), so it only works on devices that have them. Other AYN consoles (Odin 2, Odin 2 Mini, Odin 3, Thor) may use the same driver but are untested. On consoles without those files (for example Retroid Pocket) the app shows "Impossibile controllare i LED su questo dispositivo" and stops; nothing is changed.

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
