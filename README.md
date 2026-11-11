# Private Lock (trusted WiFi fork)

[![License](https://img.shields.io/github/license/wesaphzt/privatelock.svg?color=green)](LICENSE)
[![Build APK](https://github.com/Kurburis/privatelock/actions/workflows/build.yml/badge.svg)](https://github.com/Kurburis/privatelock/actions/workflows/build.yml)

Private Lock locks your phone when it detects a sudden movement, for example when a person
takes the phone out of your hand. This fork adds trusted WiFi networks. At home, the lock
pauses, and when you leave, it starts again.

This fork is based on:
- [wesaphzt/privatelock](https://github.com/wesaphzt/privatelock), the original app (last update 2019).
- [makindotcc/privatelock](https://github.com/makindotcc/privatelock), which updated the build for current Android.

<p align="center">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01-main.png?raw=true" width="200" height="400"/>
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02-test-sensitivity.png?raw=true" width="200" height="400"/>
</p>

## Install
Get the APK from [Releases](https://github.com/Kurburis/privatelock/releases).

To get updates automatically, use [Obtainium](https://github.com/ImranR98/Obtainium).
Add the app with this URL:

```
https://github.com/Kurburis/privatelock
```

Before you install, remove all other versions of Private Lock (for example from F-Droid).
They have a different signing key, so Android cannot install this version over them.
If Android shows a "conflict" error, a different version is still installed.
Look for it in all user profiles, including a work profile.

## Set up
1. Open the app and allow device admin. The app needs it to lock the screen.
2. Set the sensitivity with the slider. Use **Test the lock sensitivity** to try it.
   A lower value locks on a smaller movement.
3. Tap **Start**.
4. Turn off battery optimization for the app, so that Android does not stop the service.

## Trusted WiFi
1. Open **Settings** and turn on **Pause on trusted WiFi**.
2. Connect to your home WiFi, then tap **Add current WiFi**.
3. Allow location access. When Android asks, select **Allow all the time**.
   Android shows the WiFi name to an app in the background only with this access.
   The app reads only the WiFi name and the router address. It does not get your location.
4. Keep the system **Location** switch on. Android hides the WiFi name while it is off.

While the phone is on a trusted network, the notification shows
"Paused on trusted WiFi" and the network name.

By default, the name (SSID) and the router address (BSSID) must both match.
If your home has more than one access point (mesh or repeater), do one of these:
- Add the network once near each access point.
- Turn on **Match by name only**. This is less secure, because any person can make
  a hotspot with the same name.

The lock stays on if the app cannot read the network. This occurs when the
permission is missing, when Location is off, or when the network is not on the list.

### Limits
- After you leave home, the lock stays off until the phone loses the WiFi connection.
  This can take some seconds.
- A person who copies your network name and your router address can make a fake
  network that the app trusts.

## Changes in this fork
- **Trusted WiFi** (see above).
- **Original lock detection.** The phone locks on a sudden movement. The app compares
  two accelerometer readings in sequence, as the original app did. An earlier version
  of this fork locked only on a long movement. That version was too slow, so it was removed.
- **Updated build** (from makindotcc). The app builds and runs on current Android versions.
- **Fixes:**
  - The app icon shows correctly. Before, Android 8 and later showed an empty grey icon.
  - A movement while the screen is already off does not force a PIN lock.
  - The START and STOP menu items show the correct service state.

## Features
- Lock the phone when the movement is above the threshold
- Animation to test and see the lock sensitivity
- Notification while the service runs
- Start the service when the phone starts
- Home screen widget
- Pause the service for a time that you set
- Pause automatically on trusted WiFi networks

## Permissions
| Permission | Purpose |
|---|---|
| BIND_DEVICE_ADMIN | Lock the screen |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE | Run the lock service |
| POST_NOTIFICATIONS | Show the service notification |
| RECEIVE_BOOT_COMPLETED, WAKE_LOCK | Start the service when the phone starts |
| VIBRATE | Vibrate on lock (optional) |
| ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE | Trusted WiFi |
| ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION | Trusted WiFi. Android shows the WiFi name only to apps with location access. |

## Known issues
- After the app locks the phone, you must use your PIN to unlock. Fingerprint and
  face unlock do not work for that unlock. This is an Android limit.
- If the service stops, turn off battery optimization for the app.

## Building
GitHub Actions builds the APK and runs the unit tests on each push
(see [`.github/workflows/build.yml`](.github/workflows/build.yml)).

To sign release builds with a fixed key, set these repository secrets:
- `SIGNING_KEYSTORE_BASE64` (a PKCS12 keystore, base64-encoded)
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`

Without these secrets, CI builds a debug APK.

To publish a release, increase `versionCode` and `versionName` in `app/build.gradle`.
Then push a tag that starts with `v`, for example `v1.5-trustedwifi`.
CI then attaches the signed APK to a GitHub release.

## Privacy
No ads and no tracking. The app does not connect to the internet.

## License
[GPL v3.0](LICENSE)

## Credits
- [wesaphzt](https://github.com/wesaphzt), the original author. You can support the
  original author with these addresses:
  - BTC: 1GCkvAg9oG79niQTbh6EH9rPALQDXKyHKK
  - LTC: LV687s3wVdhmLZyJMFxomJHdHFXeFAKT5R
  - ETH: 0x785a8804c85b88683a5cce5e53f60878831e5d03
  - XMR: 43Vijzdt3y42mmT954rSYPjXYabDsjYEV2KyhxfC46JibR2ny9VmRS1fjdJTHxxPVPFE8ajgArwjWfyaRgjh9vcNAwmkfJj
- [makindotcc](https://github.com/makindotcc), who updated the build for current Android.
- The idea comes from [PluckLockEx](https://github.com/0xFireball/PluckLockEx) and
  [PluckLock](https://github.com/SyntaxBlitz/PluckLock).
