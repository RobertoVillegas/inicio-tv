# The TCL in my living room

Inicio was built for this TV. This page is the full record of how it's set up: the hardware, the debloat, the sleep timers, the memory numbers and the traps I hit along the way. Everything was done over ADB from a Mac, with no root, and it's all reversible.

## The TV

| | |
|---|---|
| Model | TCL "Smart TV Pro" (device `G10`), 55", 3840×2160 panel |
| OS | Google TV on Android 12, build `STT2.230203.001`, security patch 2025-12-05 |
| SoC | Realtek RTD2875P (`rtd6748`) |
| RAM / storage | 2 GB / 8 GB (1.8 GB and 7.9 GB usable) |
| ABI | **`armeabi-v7a` only (32-bit).** Apps with native code need their `armeabi-v7a` build, never `arm64-v8a` |
| UI resolution | **1920×1080.** Only video plays at 4K, so wallpapers bigger than 1920 px just waste memory |

## Memory

Measured on this TV with the same method for both: reboot, wait until things settle, then five `dumpsys meminfo` samples 30 s apart. The Google TV numbers were taken with the debloat already in place.

| Home screen | Average PSS |
|---|---|
| Google TV (`launcherx` + `launcherx:coreservices`) | **126 MB** (94.6 + 31.8) |
| Inicio | **43 MB** |

That's about two thirds less, on a TV with 1.8 GB of usable RAM.

## Caring for the panel

`instalar-inicio.sh` sets these timers:

| Setting | Factory | Now |
|---|---|---|
| Screensaver | Google's ambient mode (with promoted content) after 10 min | **Paisajes** (from Inicio) after **5 min** |
| Screen off | ~19 min | ~19 min by day, **10 min at night** |
| Power off with no remote input, even during video | never | **4 h** |

Nothing on screen stands still. Every photo slowly zooms and pans, and is replaced every 3 min on the home screen and every 75 s in the screensaver. In the screensaver the clock also moves to a different corner every 4 min. After sunset (the real one, for the TV's city) the photos darken and warm up, and Inicio lowers the screen-off timer to 10 minutes. In the morning it goes back to ~19.

## Debloat

```bash
./aplicar.sh fase1-desactivar.txt   # disable the packages in the list
./instalar-smarttube.sh             # SmartTube, so guests can watch YouTube without anyone's account
./restaurar-todo.sh                 # undo everything and reboot
```

The debloat uses `pm disable-user --user 0` and never uninstalls anything. A disabled app still appears under *Settings → Apps*, where anyone can turn it back on with the remote, no computer needed. [`respaldo-original/`](../respaldo-original) has the factory package state captured before any change.

- **Unused streaming apps:** Disney+, Max, Paramount+, Tubi, Twitch, PrendeTV, Downloader and an unidentified sideload (`com.flix.tv`).
- **Google:** the Assistant and voice search (`katniss`), YouTube Music, Play Games, Play Movies, Feedback, and the ambient screensaver (`dreamx`).
- **TCL:** ad-supported FAST channels (`channelplus`), store demo mode (`esticker`, `exhibit`), Alexa, MagiConnect, the promo inbox (`messagebox`), manuals, and the content feed (`waterfall.overseas`).

Kept on purpose: TCL's TV app (it owns the HDMI inputs), Chromecast, AirPlay, Play Store and Settings.

`restaurar-tv.sh` is the same undo, run on the TV itself. A copy lives at `/sdcard/restaurar-tv.sh`, so it can be run from any computer with `adb shell sh /sdcard/restaurar-tv.sh`. A factory reset removes it along with everything else.

## Gotchas

- **Google's setup app hijacks the Home button.** With Google TV's launcher disabled, `com.google.android.tungsten.setupwraith/.RecoveryActivity` outranks any custom home screen, even when Inicio holds the `HOME` role. Shell can't disable a single component of a system app, so `poner-inicio.sh` disables the whole setup app along with the launcher (setup is long done), and `rescate-launcher.sh` turns it back on.
- **TCL blocks third-party services from starting on their own.** The log reads `TclAppBoot: callee_does't_have_OP_AUTO_START_permission`, and a custom screensaver closes the instant it opens. The fix is `cmd appops set <package> APP_AUTO_START allow`; `instalar-inicio.sh` runs it.
- **Wait about 15 s after enabling or disabling packages before you reboot.** If you reboot sooner, PackageManager may not have saved the change, and it gets reverted.
- **If the IP changes,** the TV announces itself over mDNS: `adb mdns services` and look for `_adb._tcp`.
- **If macOS says "No route to host" for every LAN device,** that's Local Network Privacy, not your router. Grant your terminal access under *System Settings → Privacy & Security → Local Network*.
