#!/bin/bash
# Gives the Home button back to the factory home screen (Google TV or Android TV), for an emergency or to
# stop using Inicio. Re-enables what set-home.sh recorded on the TV; without a record, tries Google's known ones.
#
# Usage:  ./restore-launcher.sh [tv-ip]      DRY_RUN=1 ./restore-launcher.sh   only show what it would do
set -u
TV="${1:-${TV:-192.168.0.122}}"
[[ "$TV" == *:* ]] || TV="$TV:5555"
RECORD=/sdcard/inicio-launchers.txt
# In order of preference: Google TV, Android TV, old Android TV, and Google TV's setup app.
KNOWN="com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher com.google.android.tungsten.setupwraith"

read_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
run() { if [ "${DRY_RUN:-0}" = 1 ]; then echo "  (dry run) $*"; else adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; fi; }

adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "Can't reach the TV ($TV)."; exit 1; }

record=$(read_tv cat "$RECORD" 2>/dev/null | grep -v 'No such file')
packages=$({ grep -v '^previous=' <<<"$record"; tr ' ' '\n' <<<"$KNOWN"; } | grep . | sort -u)
for p in $packages; do
  read_tv pm path "$p" | grep -q package: || continue
  echo "Enabling $p"
  run pm enable "$p" >/dev/null
done

previous=$(grep '^previous=' <<<"$record" | cut -d= -f2)
if [ -z "$previous" ]; then
  for p in $KNOWN; do
    [ "$p" = com.google.android.tungsten.setupwraith ] && continue
    read_tv pm path "$p" | grep -q package: || continue
    previous=$(read_tv cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
      tr -d ' ' | grep -m1 "^$p/") && break
  done
fi
if [ -z "$previous" ] && [ "${DRY_RUN:-0}" = 1 ]; then
  previous="(whichever appears after re-enabling)"
fi
[ -n "$previous" ] || { echo "Couldn't find the factory home screen on this TV."; exit 1; }

echo "Home screen: $previous"
run cmd package set-home-activity "$previous"
run cmd role add-role-holder --user 0 android.app.role.HOME "${previous%%/*}"
run rm -f "$RECORD"
# Otherwise Inicio would notice at boot that it's no longer home and open itself to warn about it.
# As a plain app it stops showing what's playing; install-inicio.sh turns that back on.
run cmd notification disallow_listener dev.robertovillegas.inicio/dev.robertovillegas.inicio.MediaListener
# Google's home screen answers the remote's app buttons again.
run "a=\$(settings get secure enabled_accessibility_services | tr : '\\n' | grep -v RemoteButtons | paste -sd: -)
  if [ -n \"\$a\" ]; then settings put secure enabled_accessibility_services \$a; else settings delete secure enabled_accessibility_services; fi"
[ "${DRY_RUN:-0}" = 1 ] && exit 0

echo "Waiting 15 s for Android to save the change, then rebooting the TV…"
sleep 15
adb -s "$TV" reboot
echo "Done. Inicio is still installed; open it as an app or remove it in Settings > Apps."
