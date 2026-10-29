#!/bin/bash
# Installs Inicio (the latest GitHub release, or built here with BUILD=1), grants its permissions and
# makes it the screensaver, with timers that look after the panel. Making it the home screen is set-home.sh.
#
# Usage:  ./install-inicio.sh [tv-ip]        BUILD=1 ./install-inicio.sh   build from source
#         NO_APK=1 ./install-inicio.sh        only the permissions and timers (check.sh uses this)
set -eu
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
if [ "${NO_APK:-0}" = 1 ]; then
  apk=""
elif [ "${BUILD:-0}" = 1 ]; then
  (cd "$(dirname "$0")/inicio" && ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" ./gradlew assembleRelease -q)
  apk="$(dirname "$0")/inicio/app/build/outputs/apk/release/app-release.apk"
else
  apk="$(mktemp -d)/inicio.apk"
  curl -fsSL -o "$apk" https://github.com/RobertoVillegas/inicio-tv/releases/latest/download/inicio.apk
fi
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "Can't reach the TV ($TV). Did you accept debugging on screen?"; exit 1; }
[ -z "$apk" ] || adb -s "$TV" install -r "$apk"
adb -s "$TV" shell '
# TCL blocks third-party services from starting on their own; without this the screensaver closes at once.
# Other brands lack this app op and the command just warns.
cmd appops set dev.robertovillegas.inicio APP_AUTO_START allow 2>/dev/null
cmd notification allow_listener dev.robertovillegas.inicio/dev.robertovillegas.inicio.MediaListener
pm grant dev.robertovillegas.inicio android.permission.WRITE_SECURE_SETTINGS
cmd appops set dev.robertovillegas.inicio REQUEST_INSTALL_PACKAGES allow
settings put secure screensaver_enabled 1
settings put secure screensaver_components dev.robertovillegas.inicio/.LandscapesDream
settings put secure screensaver_default_component dev.robertovillegas.inicio/.LandscapesDream
settings put system screen_off_timeout 300000     # screensaver after 5 min without the remote
settings put secure sleep_timeout 1139000         # screen off after ~19 min by day; Inicio lowers it to 10 at night
settings put secure attentive_timeout 14400000    # power off after 4 h without the remote, even during video
'
echo "Inicio ${apk:+installed and }set up as the screensaver."
