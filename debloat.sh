#!/bin/bash
# Usage:  ./debloat.sh debloat-list.txt
set -u
TV="${TV:-192.168.0.122:5555}"
adb connect "$TV" >/dev/null 2>&1
adb -s "$TV" get-state >/dev/null 2>&1 || { echo "Can't reach the TV ($TV)."; exit 1; }
grep -vE '^\s*(#|$)' "$1" | while read -r p; do
  printf '%-45s ' "$p"; adb -s "$TV" shell "pm disable-user --user 0 $p" </dev/null | tail -1
done
# TCL's "diagnostic data" upload, on from the factory.
adb -s "$TV" shell settings put global tcl_diagnostic_status 0
echo "Wait ~15 s before rebooting so the changes are saved."
