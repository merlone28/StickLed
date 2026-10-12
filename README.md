# StickLed

Android app for the AYN Odin 2 family: the joystick LEDs follow the colours of the screen (left half of the screen -> left stick, right half -> right stick).

## How it works
- Captures the screen at 96 px wide with MediaProjection and averages the left and right halves.
- Writes `1-R:G:B` to `/sys/class/sn3112l/led/brightness` (left) and `/sys/class/sn3112r/led/brightness` (right), the same files the Odin settings app uses. Brightness follows the level set in the Odin settings.
- On stop, the colours saved in the Odin settings are restored.

Tested on an Odin 2 Portal (Android 13). Other devices are untested.

## Build
```
./gradlew assembleDebug
```
Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir`).
