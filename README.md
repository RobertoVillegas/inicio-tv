# Inicio

**A quiet, ad-free home screen for Google TV and Android TV.**

Google TV's home screen opens on a full-width ad with a row of sponsored picks under it, and there's no setting to switch them off. Inicio replaces it with a slowly drifting landscape, the time, the weather, where the photo was taken, and only the apps you actually use. It doubles as the screensaver, and it's built so the panel never sits on a still image.

| Before: stock Google TV | After: Inicio |
|---|---|
| ![Google TV home with a full-screen promo and sponsored rows](docs/google-tv-before.jpg) | ![Inicio home: misty Estonian bog, clock, weather and an app dock](docs/inicio-home.jpg) |

| Screensaver, with what's playing | The dock's HDMI tiles |
|---|---|
| ![The Paisajes screensaver on an Andaman beach, showing the Spotify song above the clock](docs/screensaver-now-playing.jpg) | ![The end of the dock: Spotify, SmartTube, All apps and three HDMI tiles](docs/dock-hdmi.jpg) |

*The interface is in Spanish because it was made for a Mexican household.*

## What it does

- **Landscapes** from [Wikimedia Commons](https://commons.wikimedia.org/) featured pictures: mountains, lakes, forests, beaches, waterfalls and coasts. Each one shows where it was taken and who took it. About 40 are kept on the TV, so it works offline.
- **Your own photos.** Scan a QR code with your phone and pick photos. They travel straight to the TV over your home network, with no account or cloud. Show them alone or mixed with the landscapes, filling the screen or whole, and pin a favorite.
- **Clock and weather.** Current conditions, the day's high and low, the next sunset or sunrise, and a three-hourly forecast, from [Open-Meteo](https://open-meteo.com/).
- **Now playing.** Cover art, song and artist from Spotify, SmartTube or any media app, on the home screen and the screensaver.
- **A dock you arrange with the remote.** Hold OK to move or remove an app, and add any app from *All apps*. Each HDMI input gets its own tile with the name you gave it.
- **Kind to the panel.** Photos always zoom and pan slowly, the screensaver moves the clock between corners, and after the real sunset everything dims and the TV turns off sooner.
- **Picture-frame mode.** After 45 s without a key press the dock slides away and only the photo is left.
- **Updates itself.** When a new release is out, the `•••` button shows an arrow, and installing it takes one confirmation on the remote.
- **Light.** It uses 43 MB of memory, against 126 MB for Google TV's home screen on the same TV ([measurements](docs/tcl-sala.md#memory)).

## Install

Inicio runs on Google TV and Android TV with Android 9 or later. On Fire TV it runs as a regular app, but Amazon doesn't let anything replace its home screen.

### Just try it

In the [Downloader](https://www.aftvnews.com/downloader/) app on the TV, enter code **`6547657`**. It always fetches the latest release. Inicio installs as a regular app you can open from the apps list. Making it the home screen needs the full setup below.

### Make it the home screen

You need a computer with ADB (`brew install --cask android-platform-tools` on a Mac) on the same network as the TV.

1. On the TV, go to *Settings → System → About* and press OK seven times on the build number. Then go to *Settings → System → Developer options* and turn on *USB debugging*.
2. Connect, and accept the prompt that appears on the TV:
   ```bash
   adb connect <tv-ip>:5555
   ```
3. Install Inicio, then hand it the Home button:
   ```bash
   ./instalar-inicio.sh <tv-ip>    # latest release, permissions, screensaver, sleep timers
   ./poner-inicio.sh <tv-ip>       # make it the home screen
   ```

`instalar-inicio.sh` grants what Inicio can't ask for on its own: reading what's playing, changing the sleep timer at night, and installing its own updates. `poner-inicio.sh` asks Android which apps can be the home screen and shows you the list. When you confirm, it disables them and writes down on the TV what it changed. Run either one with `SIMULAR=1` to see what it would do without changing anything.

### Going back

```bash
./rescate-launcher.sh <tv-ip>
```

It re-enables the original home screen and anything else `poner-inicio.sh` disabled, hands the Home button back, and reboots. Inicio stays installed as a regular app.

## In this repo

```
inicio/                  The Android app (Kotlin, no dependencies)
instalar-inicio.sh       Install Inicio and its permissions, screensaver and timers
poner-inicio.sh          Make Inicio the home screen
rescate-launcher.sh      Give the original home screen back
docs/tcl-sala.md         The TCL Google TV it was built on: specs, debloat, timers, memory, gotchas
docs/compilar.md         Building, signing and releasing
```

The other scripts (`aplicar.sh`, `fase1-desactivar.txt`, `instalar-smarttube.sh`, `restaurar-*`) are that TV's debloat, described in [docs/tcl-sala.md](docs/tcl-sala.md). Script names are in Spanish.

## Credits

Inspired by [CleanFrame](https://github.com/nthnclrk/CleanFrame), which does the same for Samsung's The Frame, and by this [TCL Google TV walkthrough](https://www.youtube.com/watch?v=i0ytu6k8uac). The sister repo [cobia-tv](https://github.com/RobertoVillegas/cobia-tv) covers another TV in the same house.

- Photographs: their authors on [Wikimedia Commons](https://commons.wikimedia.org/), credited on screen under their own licenses (mostly CC BY-SA).
- Place names: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, via Nominatim.
- Weather: [Open-Meteo](https://open-meteo.com/) (CC BY 4.0). Approximate location: [ipapi.co](https://ipapi.co/).

MIT licensed (see [LICENSE](LICENSE)).
