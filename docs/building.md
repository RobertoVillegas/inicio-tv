# Building and releasing

## Building

You need JDK 21 and the Android SDK (`brew install --cask android-commandlinetools`, then `sdkmanager "platforms;android-35"`):

```bash
cd inicio && ./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
BUILD=1 ./install-inicio.sh <tv-ip>        # build, install and set up in one go
```

The app is plain Kotlin with no dependencies and no native code, about 150 KB.

## Signing

If `~/.android/inicio-release.properties` exists (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), the build is signed with that key. Otherwise it uses your local debug key, which is fine for a fork. Android only installs an update signed with the same key, so if you switch keys you have to uninstall first.

## Releasing

Push a signed tag such as `v0.5.0` and [`release.yml`](../.github/workflows/release.yml) builds the APK and publishes it twice: as `inicio-vX.Y.Z.apk` and as `inicio.apk`. The second name gives a link that always points at the newest version:

```
https://github.com/RobertoVillegas/inicio-tv/releases/latest/download/inicio.apk
```

That link is what the Downloader codes and `install-inicio.sh` use, and Inicio itself checks the latest release to offer updates. Bump `versionCode` and `versionName` in `inicio/app/build.gradle.kts` before tagging, or installed copies won't see the new version as newer. Re-pushing an existing tag replaces its APK instead of failing.

The workflow needs three repository secrets: `INICIO_KEYSTORE_B64` (the keystore in base64), `INICIO_STORE_PASSWORD` and `INICIO_KEY_PASSWORD`.

## Downloader codes

Codes from the [AFTVnews shortener](https://go.aftvnews.com/) are permanent and can't be edited.

| Code | Points to |
|---|---|
| `6547657` | `inicio-tv/releases/latest/download/inicio.apk` (current) |
| `4993644` | `tcl-tv/releases/latest/download/inicio.apk`, the repo's old name. It keeps working because GitHub redirects renamed repos, as long as no new repo takes the name `tcl-tv` |
