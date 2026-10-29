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
| UI resolution | **1920×1080** on a 3840×2160 panel (`wm size` override), so anything Inicio draws is upscaled 2× by the TV. Downloading photos at 2208 px instead of 1920 measured no sharper. Only a SurfaceView, the way video players do it, could show photos at full 4K |

## Memory

Measured on this TV with the same method for both: reboot, wait until things settle, then five `dumpsys meminfo` samples 30 s apart. The Google TV numbers were taken with the debloat already in place.

| Home screen | Average PSS |
|---|---|
| Google TV (`launcherx` + `launcherx:coreservices`) | **126 MB** (94.6 + 31.8) |
| Inicio | **43 MB** |

That's about two thirds less, on a TV with 1.8 GB of usable RAM. Inicio 0.5.0 settles at 45 MB with the same method.

When another app is in front and Android runs low on memory, Inicio drops its two background photos (about 10 MB of native heap; PSS goes from 41.7 to 36.8 MB with YouTube in front) and loads a fresh one on the way back. It never sees `TRIM_MEMORY_BACKGROUND`, though: the system keeps its notification listener bound, which makes it a foreground process, so it reacts to the `RUNNING_LOW` and `RUNNING_CRITICAL` levels instead.

`./measure.sh` repeats all of this and adds smoothness and CPU. On 0.5.1:

| | |
|---|---|
| Home after the process was killed | about 370 ms (a true cold start never happens: Android revives the process within two seconds to reconnect the listener) |
| Dock, 24 key presses | 0.6–1.5 % janky frames, median frame 13 ms |
| Idle memory | 45 MB PSS |
| Idle CPU | 2 % of one core |

The photo's slow zoom moves it by under 1 px a second. Up to 0.5.0 it was animated at 60 fps, which cost about 36 % of a core between Inicio's RenderThread (11 %) and SurfaceFlinger (22 %). Since 0.5.1 it steps four times a second and the same idle screen costs about 3 %. The photo still sits in a hardware layer: drawing it directly measured 5 % sharper but made the dock janky (30 % of frames), because every dock animation then repainted the full-screen photo.

## Caring for the panel

`install-inicio.sh` sets these timers:

| Setting | Factory | Now |
|---|---|---|
| Screensaver | Google's ambient mode (with promoted content) after 10 min | **Paisajes** (from Inicio) after **5 min** |
| Screen off | ~19 min | ~19 min by day, **10 min at night** |
| Power off with no remote input, even during video | never | **4 h** |

Nothing on screen stands still. Every photo slowly zooms and pans, and is replaced every 3 min on the home screen and every 75 s in the screensaver. In the screensaver the clock also moves to a different corner every 4 min. After sunset (the real one, for the TV's city) the photos darken and warm up, and Inicio lowers the screen-off timer to 10 minutes. In the morning it goes back to ~19.

## Debloat

```bash
./debloat.sh debloat-list.txt       # disable the packages in the list
./install-smarttube.sh              # SmartTube, so guests can watch YouTube without anyone's account
./undo-debloat.sh                   # undo everything and reboot
```

The debloat uses `pm disable-user --user 0` and never uninstalls anything. A disabled app still appears under *Settings → Apps*, where anyone can turn it back on with the remote, no computer needed. [`factory-backup/`](../factory-backup) has the factory package state captured before any change.

- **Unused streaming apps:** Disney+, Max, Paramount+, Tubi, Twitch, PrendeTV, Downloader and an unidentified sideload (`com.flix.tv`).
- **Google:** the Assistant and voice search (`katniss`), YouTube Music, Play Games, Play Movies, Feedback, and the ambient screensaver (`dreamx`).
- **TCL:** ad-supported FAST channels (`channelplus`), store demo mode (`esticker`, `exhibit`), Alexa, MagiConnect, the promo inbox (`messagebox`), manuals, and the content feed (`waterfall.overseas`).

Kept on purpose: TCL's TV app (it owns the HDMI inputs), Chromecast, AirPlay, Play Store and Settings.

`undo-debloat-on-tv.sh` is the same undo, run on the TV itself. `undo-debloat.sh` copies it to `/sdcard/undo-debloat-on-tv.sh`, so afterwards it can be run from any computer with `adb shell sh /sdcard/undo-debloat-on-tv.sh`. A factory reset removes it along with everything else.

## Gotchas

- **Google's setup app hijacks the Home button.** With Google TV's launcher disabled, `com.google.android.tungsten.setupwraith/.RecoveryActivity` outranks any custom home screen, even when Inicio holds the `HOME` role. Shell can't disable a single component of a system app, so `set-home.sh` disables the whole setup app along with the launcher (setup is long done), and `restore-launcher.sh` turns it back on.
- **TCL blocks third-party services from starting on their own.** The log reads `TclAppBoot: callee_does't_have_OP_AUTO_START_permission`, and a custom screensaver closes the instant it opens. The fix is `cmd appops set <package> APP_AUTO_START allow`; `install-inicio.sh` runs it.
- **Wait about 15 s after enabling or disabling packages before you reboot.** If you reboot sooner, PackageManager may not have saved the change, and it gets reverted.
- **If the IP changes,** the TV announces itself over mDNS: `adb mdns services` and look for `_adb._tcp`.
- **If macOS says "No route to host" for every LAN device,** that's Local Network Privacy, not your router. Grant your terminal access under *System Settings → Privacy & Security → Local Network*.
- **The remote's buttons, without Google TV's launcher.** Input still works (`com.tcl.suspension` opens TCL's input panel) and so do the app buttons (TCL maps them, e.g. `KEYCODE_BUTTON_1` opens YouTube). What goes dead is what Android routed to Google's launcher: Guide, Live TV, USB and holding Home. Holding Home asks SystemUI for the notification panel, and SystemUI only looks for it inside Google's launcher package, so another app can't answer it. The only way back is an accessibility service that filters keys, and on a TCL that's known to stop the power button from waking the set, so Inicio leaves those buttons alone.
- **HDMI states can't tell an empty port.** All three ports report `INPUT_STATE_CONNECTED_STANDBY` with nothing plugged in or with a loose cable, and `dumpsys hdmi_control` shows no hot-plug on any of them. Inicio shows an HDMI tile only for `INPUT_STATE_CONNECTED`.
- **After a firmware update**, run `./check.sh`; `FIX=1 ./check.sh` re-disables what came back and hands Home to Inicio again. Inicio itself notices on the next boot and says so on screen.
