#!/bin/bash
# Checks that the TV is still how the scripts left it. Mostly for after a firmware update, which re-enables
# disabled packages and hands the Home button back to Google TV. Checks debloat-list.txt and what
# set-home.sh disabled, that Inicio is the home screen, and its permissions. FIX=1 puts back what's off.
#
# Usage:  ./check.sh [tv-ip]          FIX=1 ./check.sh
# Exits 0 when everything is in order (or got fixed), 1 when something is off, 2 when the TV is unreachable.
set -u
cd "$(dirname "$0")"
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
INICIO=dev.robertovillegas.inicio
RECORD=/sdcard/inicio-launchers.txt
UNTOUCHABLE="$INICIO com.android.tv.settings com.android.settings android"

read_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
off=0
bad() { echo "  ✗ $*"; off=1; }
good() { echo "  ✓ $*"; }

adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "Can't reach the TV ($TV). Did you accept debugging on screen?"; exit 2; }
read_tv pm path "$INICIO" | grep -q package: || { echo "Inicio isn't installed on $TV: run ./install-inicio.sh"; exit 1; }
echo "TV: $(read_tv getprop ro.product.manufacturer) $(read_tv getprop ro.product.model), firmware $(read_tv getprop ro.build.id), patch $(read_tv getprop ro.build.version.security_patch)"

echo "Disabled packages"
disabled=$(read_tv pm list packages -d | cut -d: -f2)
installed=$(read_tv pm list packages | cut -d: -f2)
wanted=$({ grep -vE '^\s*(#|$)' debloat-list.txt; read_tv cat "$RECORD" 2>/dev/null | grep -v -e '^previous=' -e 'No such file'; } | sort -u)
reenabled=$(comm -12 <(sort -u <<<"$installed") <(comm -23 <(echo "$wanted") <(sort -u <<<"$disabled")))
if [ -n "$reenabled" ]; then
  bad "enabled again: $(echo $reenabled)"
else
  good "all $(grep -c . <<<"$wanted") on the list are still off (or gone)"
fi

echo "Home screen"
home=$(read_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
others=$(read_tv cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
  grep -E '^ +[A-Za-z0-9_.]+/' | tr -d ' ' | cut -d/ -f1 | sort -u | grep -vxF -f <(tr ' ' '\n' <<<"$UNTOUCHABLE"))
if [[ "$home" == "$INICIO/"* ]]; then good "Home opens Inicio"; else bad "Home opens $home"; fi
[ -z "$others" ] || bad "other home screens are enabled: $(echo $others)"

echo "Inicio's permissions"
permissions_off=0
read_tv settings get secure enabled_notification_listeners | grep -q "$INICIO" && good "reads what's playing" ||
  { bad "can't read what's playing (or guard the Home button)"; permissions_off=1; }
read_tv dumpsys package "$INICIO" | grep -q 'WRITE_SECURE_SETTINGS: granted=true' && good "sets the night sleep timer" ||
  { bad "can't set the night sleep timer"; permissions_off=1; }
read_tv settings get secure screensaver_components | grep -q "$INICIO" && good "Landscapes is the screensaver" ||
  { bad "the screensaver is no longer Landscapes"; permissions_off=1; }

# Information only: packages that didn't exist in the factory backup. Apps you installed, or ones an update
# brought; if one is new junk, add it to debloat-list.txt.
new=$(comm -13 factory-backup/packages-including-uninstalled.txt <(read_tv pm list packages -u | cut -d: -f2 | sort -u))
if [ -n "$new" ]; then
  echo "Packages not in the factory backup:"
  sed 's/^/    /' <<<"$new"
fi

[ "$off" = 0 ] && { echo "All in order."; exit 0; }
[ "${FIX:-0}" = 1 ] || { echo "To fix it:  FIX=1 ./check.sh ${1:-}"; exit 1; }

echo
echo "Fixing…"
for p in $reenabled; do
  printf '  %-45s ' "$p"; read_tv pm disable-user --user 0 "$p" | tail -1
done
[ "$permissions_off" = 0 ] || NO_APK=1 ./install-inicio.sh "$TV"
if [[ "$home" != "$INICIO/"* ]] || [ -n "$others" ]; then
  ./set-home.sh "$TV" </dev/null
else
  echo "Done. Wait about 15 s before rebooting the TV so Android saves the changes."
fi
