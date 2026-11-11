# Private Lock (fork)

[![License](https://img.shields.io/github/license/wesaphzt/privatelock.svg?color=green)](LICENSE)

This is a hard fork of [wesaphzt/privatelock](https://github.com/wesaphzt/privatelock).
The upstream app had not been touched in a while and no longer built or ran cleanly on modern
Android, and the lock detection fired on the wrong things. This fork brings the build up to date
and reworks how movement is detected.

## What is different in this fork

**The build was brought up to date.** Dependencies and toolchain were updated and
deprecated APIs replaced, so it builds and runs cleanly on current Android.

**Lock detection was reworked.** The original approach reacted to sudden spikes, so setting the
phone down or dropping it would trigger a lock instead of an actual pickup. The new detector only
locks once movement stays above the threshold for a sustained moment, so a brief bump does not set
it off. Defaults are tuned to be tolerant, so handling the phone in your hand does not trigger a lock.

**Trusted WiFi.** You can mark WiFi networks as trusted, for example your home network.
While the phone is connected to a trusted network, motion does not lock it. See
[Trusted WiFi](#trusted-wifi) below.

**Smaller fixes.** Locking is now guarded by `isInteractive()` so a spike while the screen is 
already off cannot force a PIN lock, and the START/STOP menu items now track the real service state
instead of getting out of sync.

---

A simple app to automatically lock your phone based on movement force, or the acceleration to be more accurate.

Private Lock can help protect your privacy and security by monitoring the accelerometer in the background and if the threshold is breached, lock the screen.

The main use cases are if your phone is taken while you're using it, or you drop your phone.
As an additional bonus, it can also help save wear and tear on your power button!

Inspired by the abandoned [PluckLockEx](https://github.com/0xFireball/PluckLockEx) project, which is based on [PluckLock](https://github.com/SyntaxBlitz/PluckLock).

<p align="center">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01-main.png?raw=true" width="200" height="400"/> 

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02-test-sensitivity.png?raw=true" width="200" height="400"/> 
</p>

## Features
- Lock phone when accelerometer threshold is breached
- Animation to help test & visualize lock sensitivity
- Notification when service is running
- Start service on boot option
- Home screen widget
- Pause service for your defined length of time
- Pause automatically on trusted WiFi networks

## Trusted WiFi
1. Open **Settings** and turn on **Pause on trusted WiFi**.
2. Connect to your home WiFi, then tap **Add current WiFi**.
3. Allow location access, and set it to **Allow all the time** when Android asks.
   Android shows the WiFi name to a background app only with this access.
   The app reads only the WiFi name and the router address. It does not request your location.
4. Keep the system **Location** switch on. Android hides the WiFi name while it is off.

By default, a network is trusted only if both the name (SSID) and the router address (BSSID)
match. If your home has more than one access point (mesh or repeater), add the network once
near each access point, or turn on **Match by name only**. Name-only matching is less secure:
anyone can set up a hotspot with the same name.

The lock stays on whenever the app cannot read the network: no permission, Location off,
or a network that is not on the list. While the lock is paused on trusted WiFi, the
notification shows the network name.

Limits to know about:
- After you leave home, the lock stays off until the phone drops the WiFi connection.
  That can take some seconds, and some tens of metres.
- A person who copies both your network name and your router address can make a fake
  network that the app trusts.

## Requirements
Device admin permission for locking screen.

## Permissions
- android.permission.RECEIVE_BOOT_COMPLETED (start on boot)
- android.permission.WAKE_LOCK (start on boot)
- android.permission.BIND_DEVICE_ADMIN (lock the screen)
- android.permission.FOREGROUND_SERVICE (run the service)
- android.permission.BIND_JOB_SERVICE (run the service)
- android.permission.ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE (trusted WiFi)
- android.permission.ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION
  (trusted WiFi: Android reveals the WiFi name only to apps with location access)

## Building
GitHub Actions builds the APK on every push (see `.github/workflows/build.yml`).
To sign release builds with a stable key, set these repository secrets:
`SIGNING_KEYSTORE_BASE64` (base64 of a PKCS12 keystore), `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`. Without them, CI builds a debug APK instead.
Push a tag such as `v1.3` to publish the APK as a GitHub release.

## Issues
To contribute, or to report issues please use the [Issue Tracker](https://github.com/makindotcc/privatelock/issues/).

### Known Issues
You will need to use your PIN code to unlock when phone lock is triggered, even when fingerprint/pattern unlock is enabled.
This is a system limitation and according to Google, a "feature, not a bug".

This is not an issue on rooted phones, however root isn't supported as of yet.

If you experience issues, please make sure battery optimizations are disabled for the app.

## Privacy
Free from ads and tracking.

## License
[GPL v3.0](LICENSE)

## Tips
If you find these apps useful, consider supporting me in some way in my mission to create simple, useful, privacy-oriented, open-source apps.

BTC: 1GCkvAg9oG79niQTbh6EH9rPALQDXKyHKK

LTC: LV687s3wVdhmLZyJMFxomJHdHFXeFAKT5R

ETH: 0x785a8804c85b88683a5cce5e53f60878831e5d03

XMR: 43Vijzdt3y42mmT954rSYPjXYabDsjYEV2KyhxfC46JibR2ny9VmRS1fjdJTHxxPVPFE8ajgArwjWfyaRgjh9vcNAwmkfJj
